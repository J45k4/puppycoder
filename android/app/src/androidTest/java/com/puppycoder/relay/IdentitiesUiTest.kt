package com.puppycoder.relay

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.puppycoder.relay.data.SshIdentity
import com.puppycoder.relay.data.SshIdentityKind
import com.puppycoder.relay.data.SshConnection
import com.puppycoder.relay.data.SshTunnelConfig
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentitiesUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun identityPageAndHopPickerFlow() {
        val repository = (compose.activity.application as PuppyCoderApplication).repository
        runBlocking {
            repository.saveIdentity(
                SshIdentity(
                    id = "identity-shot",
                    name = "Work laptop key",
                    kind = SshIdentityKind.PRIVATE_KEY,
                    username = "puppy",
                    privateKey = "KEY",
                ),
            )
            repository.saveSshConnection(
                SshConnection(
                    id = "connection-shot",
                    name = "Work laptop gateway",
                    ssh = SshTunnelConfig(host = "gateway.example", identityId = "identity-shot"),
                ),
            )
        }

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Identities").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Identities").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Work laptop key").fetchSemanticsNodes().isNotEmpty()
        }
        capture("identity-list")

        compose.onNodeWithText("Add identity", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Sign-in method").fetchSemanticsNodes().isNotEmpty()
        }
        capture("identity-add")

        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Identities").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back to computers").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("SSH connections").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Add computer", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Route").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose SSH connection").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Work laptop gateway").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Work laptop gateway").performClick()
        capture("hop-identity-picked")

        compose.onNodeWithContentDescription("SSH computer editor fields")
            .performScrollToNode(hasText("Add computer"))
        compose.onNodeWithContentDescription("Close").performClick()
        runBlocking {
            repository.deleteSshConnection("connection-shot")
            repository.deleteIdentity("identity-shot")
        }
    }

    private fun capture(name: String) {
        val sheet = compose.onAllNodesWithContentDescription("Identity editor fields")
            .fetchSemanticsNodes()
            .isNotEmpty()
        val editor = compose.onAllNodesWithContentDescription("SSH computer editor fields")
            .fetchSemanticsNodes()
            .isNotEmpty()
        val node = when {
            sheet -> compose.onNodeWithContentDescription("Identity editor fields")
            editor -> compose.onNodeWithContentDescription("SSH computer editor fields")
            else -> compose.onRoot()
        }
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.filesDir, "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
