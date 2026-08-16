package com.puppycoder.relay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileReferenceTest {
    @Test
    fun resolvesRelativeAndEncodedWorkspaceLinks() {
        assertEquals(
            "/work/project/docs/result file.md",
            resolveRemoteFileReference(
                workspace = "/work/project",
                reference = "docs/result%20file.md#preview",
                allowOutsideWorkspace = false,
            ),
        )
    }

    @Test
    fun keepsMarkdownLinksInsideWorkspace() {
        assertNull(
            resolveRemoteFileReference(
                workspace = "/work/project",
                reference = "../secret.txt",
                allowOutsideWorkspace = false,
            ),
        )
        assertNull(
            resolveRemoteFileReference(
                workspace = "/work/project",
                reference = "/etc/passwd",
                allowOutsideWorkspace = false,
            ),
        )
    }

    @Test
    fun allowsAbsoluteToolPathsWhenExplicitlyRequested() {
        assertEquals(
            "/tmp/render.png",
            resolveRemoteFileReference(
                workspace = "/work/project",
                reference = "file:///tmp/render.png",
                allowOutsideWorkspace = true,
            ),
        )
    }

    @Test
    fun distinguishesRemoteFilesFromNetworkLinks() {
        assertTrue(isRemoteFileReference("screenshots/result.png"))
        assertTrue(isRemoteFileReference("file:///tmp/result.png"))
        assertFalse(isRemoteFileReference("https://example.com/result.png"))
        assertFalse(isRemoteFileReference("mailto:hello@example.com"))
    }

    @Test
    fun recognizesCodexImageViewActivities() {
        assertEquals(
            "/work/project/render.png",
            remoteFileReferenceFromActivity("Image view", "/work/project/render.png"),
        )
        assertNull(remoteFileReferenceFromActivity("Command execution", "/work/project/render.png"))
    }
}
