package com.puppycoder.relay.data

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal data class RemoteFileCacheTarget(
    val remotePath: String,
    val temporary: File,
    val destination: File,
    val displayName: String,
    val mimeType: String,
)

internal class RemoteFileCache(context: Context) {
    private val directory = File(context.cacheDir, "remote-files").apply { mkdirs() }

    fun createTarget(computerId: String, remotePath: String): RemoteFileCacheTarget {
        val displayName = remotePath.substringAfterLast('/').ifBlank { "remote-file" }
        val extension = displayName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..12 && it.all(Char::isLetterOrDigit) }
        val mimeType = extension?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
            ?: fallbackMimeType(extension)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$computerId:$remotePath".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val destination = File(directory, digest + extension?.let { ".$it" }.orEmpty())
        return RemoteFileCacheTarget(
            remotePath = remotePath,
            temporary = File(directory, "$digest-${UUID.randomUUID()}.part"),
            destination = destination,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    fun complete(target: RemoteFileCacheTarget, sizeBytes: Long): DownloadedRemoteFile {
        if (!target.temporary.renameTo(target.destination)) {
            target.temporary.copyTo(target.destination, overwrite = true)
            check(target.temporary.delete()) { "Could not finalize remote-file cache" }
        }
        return DownloadedRemoteFile(
            remotePath = target.remotePath,
            localPath = target.destination.absolutePath,
            displayName = target.displayName,
            mimeType = target.mimeType,
            sizeBytes = sizeBytes,
        )
    }

    fun discard(target: RemoteFileCacheTarget) {
        target.temporary.delete()
    }

    fun maxDownloadBytes(): Long = (directory.usableSpace - MIN_RESERVED_BYTES).coerceAtLeast(0)

    private fun fallbackMimeType(extension: String?): String = when (extension) {
        "md", "markdown", "txt", "log", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx",
        "c", "cc", "cpp", "h", "hpp", "rs", "go", "sh", "bash", "zsh", "toml", "ini", "cfg",
        "properties", "gradle" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "yaml", "yml" -> "application/x-yaml"
        else -> "application/octet-stream"
    }
}

private const val MIN_RESERVED_BYTES = 128L * 1024L * 1024L
