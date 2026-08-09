package com.puppycoder.relay.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.puppycoder.relay.BuildConfig
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private const val RELEASES_URL = "https://api.github.com/repos/J45k4/puppycoder/releases?per_page=30"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1_000L

data class UpdateRelease(
    val version: String,
    val tag: String,
    val apkUrl: String,
    val sha256: String,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Downloading(val release: UpdateRelease, val progressPercent: Int?) : AppUpdateState
    data class Ready(val release: UpdateRelease, val downloadId: Long) : AppUpdateState
    data class Failed(val release: UpdateRelease, val message: String) : AppUpdateState
}

class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private var checkJob: Job? = null
    private var monitorJob: Job? = null

    val state: StateFlow<AppUpdateState> = _state

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == preferences.getLong(KEY_DOWNLOAD_ID, -1L)) {
                refreshDownload(completedId)
            }
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        restoreDownload()
        checkForUpdates()
    }

    fun checkForUpdates(force: Boolean = false) {
        if (
            checkJob?.isActive == true ||
            _state.value is AppUpdateState.Downloading ||
            _state.value is AppUpdateState.Ready ||
            _state.value is AppUpdateState.Failed
        ) {
            return
        }
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return

        checkJob = scope.launch {
            _state.value = AppUpdateState.Checking
            runCatching { withContext(Dispatchers.IO) { fetchLatestRelease() } }
                .onSuccess { release ->
                    preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    if (release == null || !isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
                        _state.value = AppUpdateState.UpToDate
                    } else {
                        enqueue(release)
                    }
                }
                .onFailure {
                    // Update checks are best-effort and should not interrupt normal app use.
                    _state.value = AppUpdateState.Idle
                }
        }
    }

    fun retryDownload() {
        val failed = _state.value as? AppUpdateState.Failed ?: return
        val previousId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (previousId >= 0) downloads.remove(previousId)
        preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
        scope.launch { enqueue(failed.release) }
    }

    fun installDownloadedUpdate(): Result<Unit> {
        val ready = _state.value as? AppUpdateState.Ready
            ?: return Result.failure(IllegalStateException("The update has not finished downloading"))
        if (!context.packageManager.canRequestPackageInstalls()) {
            return Result.failure(SecurityException("Allow PuppyCoder to install unknown apps first"))
        }
        val apkUri = downloads.getUriForDownloadedFile(ready.downloadId)
            ?: return Result.failure(IllegalStateException("The downloaded APK is no longer available"))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent) }
    }

    private fun restoreDownload() {
        val release = persistedRelease() ?: return
        if (!isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
            val previousId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
            if (previousId >= 0) downloads.remove(previousId)
            clearPersistedRelease()
            return
        }
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId >= 0) {
            refreshDownload(downloadId)
        } else {
            scope.launch { enqueue(release) }
        }
    }

    private suspend fun enqueue(release: UpdateRelease) {
        persistRelease(release)
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("PuppyCoder ${release.version}")
            .setDescription("Downloading app update")
            .setMimeType(APK_MIME_TYPE)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "puppycoder-${release.tag}.apk",
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        runCatching { downloads.enqueue(request) }
            .onSuccess { id ->
                preferences.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
                _state.value = AppUpdateState.Downloading(release, null)
                monitorDownload(id)
            }
            .onFailure { error ->
                _state.value = AppUpdateState.Failed(release, error.message ?: "Could not start the update download")
            }
    }

    private fun monitorDownload(downloadId: Long) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                if (refreshDownload(downloadId)) return@launch
                delay(1_000)
            }
        }
    }

    /** Returns true when the download reached a terminal state. */
    private fun refreshDownload(downloadId: Long): Boolean {
        val release = persistedRelease() ?: return true
        val cursor = downloads.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) {
                _state.value = AppUpdateState.Failed(release, "The update download is no longer available")
                return true
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _state.value = AppUpdateState.Downloading(release, 100)
                    verifyDownload(release, downloadId)
                    true
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    _state.value = AppUpdateState.Failed(release, "Update download failed (code $reason)")
                    true
                }
                else -> {
                    val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null
                    _state.value = AppUpdateState.Downloading(release, progress)
                    false
                }
            }
        }
    }

    private fun verifyDownload(release: UpdateRelease, downloadId: Long) {
        scope.launch {
            val verified = withContext(Dispatchers.IO) {
                val uri = downloads.getUriForDownloadedFile(downloadId) ?: return@withContext false
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                } ?: return@withContext false
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    .equals(release.sha256, ignoreCase = true)
            }
            if (verified) {
                _state.value = AppUpdateState.Ready(release, downloadId)
            } else {
                downloads.remove(downloadId)
                preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
                _state.value = AppUpdateState.Failed(release, "The downloaded APK failed its checksum verification")
            }
        }
    }

    private fun fetchLatestRelease(): UpdateRelease? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "PuppyCoder-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val releases = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub release check failed with HTTP ${response.code}")
            JSONArray(response.body?.string() ?: error("GitHub returned an empty release response"))
        }
        val candidate = parseLatestAndroidRelease(releases) ?: return null
        val sha256 = candidate.embeddedSha256 ?: fetchChecksum(candidate.checksumUrl)
        return UpdateRelease(candidate.version, candidate.tag, candidate.apkUrl, sha256)
    }

    private fun fetchChecksum(url: String?): String {
        requireNotNull(url) { "Android release is missing its SHA-256 checksum" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PuppyCoder-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val checksum = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Checksum download failed with HTTP ${response.code}")
            response.body?.string().orEmpty().trim().substringBefore(' ')
        }
        require(checksum.matches(Regex("[0-9a-fA-F]{64}"))) { "Android release has an invalid SHA-256 checksum" }
        return checksum.lowercase()
    }

    private fun persistRelease(release: UpdateRelease) {
        preferences.edit()
            .putString(KEY_VERSION, release.version)
            .putString(KEY_TAG, release.tag)
            .putString(KEY_APK_URL, release.apkUrl)
            .putString(KEY_SHA256, release.sha256)
            .apply()
    }

    private fun persistedRelease(): UpdateRelease? {
        val version = preferences.getString(KEY_VERSION, null) ?: return null
        val tag = preferences.getString(KEY_TAG, null) ?: return null
        val apkUrl = preferences.getString(KEY_APK_URL, null) ?: return null
        val sha256 = preferences.getString(KEY_SHA256, null) ?: return null
        return UpdateRelease(version, tag, apkUrl, sha256)
    }

    private fun clearPersistedRelease() {
        preferences.edit()
            .remove(KEY_VERSION)
            .remove(KEY_TAG)
            .remove(KEY_APK_URL)
            .remove(KEY_SHA256)
            .remove(KEY_DOWNLOAD_ID)
            .apply()
    }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_VERSION = "version"
        const val KEY_TAG = "tag"
        const val KEY_APK_URL = "apk_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}

internal data class ReleaseCandidate(
    val version: String,
    val tag: String,
    val apkUrl: String,
    val embeddedSha256: String?,
    val checksumUrl: String?,
)

internal fun parseLatestAndroidRelease(releases: JSONArray): ReleaseCandidate? {
    return (0 until releases.length()).mapNotNull { index ->
        val release = releases.optJSONObject(index) ?: return@mapNotNull null
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@mapNotNull null
        val tag = release.optString("tag_name")
        val version = tag.removePrefix("android-v")
        val semanticVersion = SemanticVersion.parse(version) ?: return@mapNotNull null
        if (tag != "android-v$version") return@mapNotNull null

        val assets = release.optJSONArray("assets") ?: return@mapNotNull null
        var apkUrl: String? = null
        var embeddedSha256: String? = null
        var checksumUrl: String? = null
        for (assetIndex in 0 until assets.length()) {
            val asset = assets.optJSONObject(assetIndex) ?: continue
            val name = asset.optString("name")
            when {
                name.endsWith(".apk", ignoreCase = true) -> {
                    apkUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                    embeddedSha256 = asset.optString("digest")
                        .removePrefix("sha256:")
                        .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                        ?.lowercase()
                }
                name.endsWith(".apk.sha256", ignoreCase = true) -> {
                    checksumUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                }
            }
        }
        val resolvedApkUrl = apkUrl ?: return@mapNotNull null
        semanticVersion to ReleaseCandidate(version, tag, resolvedApkUrl, embeddedSha256, checksumUrl)
    }.maxByOrNull { it.first }?.second
}

internal data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        private val pattern = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value) ?: return null
            return runCatching {
                SemanticVersion(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateVersion = SemanticVersion.parse(candidate) ?: return false
    val currentVersion = SemanticVersion.parse(current.substringBefore('-')) ?: return false
    return candidateVersion > currentVersion
}
