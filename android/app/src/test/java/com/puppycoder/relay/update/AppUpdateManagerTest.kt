package com.puppycoder.relay.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun selectsHighestPublishedAndroidReleaseWithApk() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v9.0.0",
                "draft": false,
                "prerelease": false,
                "assets": [{"name": "server.tar.gz", "browser_download_url": "https://example/server"}]
              },
              {
                "tag_name": "android-v0.4.1",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "puppycoder-android-v0.4.1.apk",
                    "browser_download_url": "https://example/0.4.1.apk",
                    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                  }
                ]
              },
              {
                "tag_name": "android-v0.10.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "puppycoder-android-v0.10.0.apk", "browser_download_url": "https://example/0.10.0.apk"},
                  {"name": "puppycoder-android-v0.10.0.apk.sha256", "browser_download_url": "https://example/0.10.0.sha256"}
                ]
              },
              {
                "tag_name": "android-v1.0.0",
                "draft": true,
                "prerelease": false,
                "assets": [{"name": "draft.apk", "browser_download_url": "https://example/draft.apk"}]
              }
            ]
            """.trimIndent(),
        )

        val release = parseLatestAndroidRelease(releases)

        assertEquals("0.10.0", release?.version)
        assertEquals("https://example/0.10.0.apk", release?.apkUrl)
        assertEquals("https://example/0.10.0.sha256", release?.checksumUrl)
        assertNull(release?.embeddedSha256)
    }

    @Test
    fun ignoresMalformedTagsAndReleasesWithoutApk() {
        val releases = JSONArray(
            """
            [
              {"tag_name": "android-v1.2", "draft": false, "prerelease": false, "assets": []},
              {"tag_name": "android-v1.2.3-beta", "draft": false, "prerelease": false, "assets": []},
              {"tag_name": "android-v1.2.3", "draft": false, "prerelease": false, "assets": [{"name": "notes.txt"}]}
            ]
            """.trimIndent(),
        )

        assertNull(parseLatestAndroidRelease(releases))
    }

    @Test
    fun comparesSemanticVersionsNumerically() {
        assertTrue(isNewerVersion("0.10.0", "0.9.99"))
        assertTrue(isNewerVersion("1.0.0", "0.999.999"))
        assertFalse(isNewerVersion("0.3.8", "0.3.8"))
        assertFalse(isNewerVersion("0.3.7", "0.3.8"))
        assertFalse(isNewerVersion("invalid", "0.3.8"))
    }
}
