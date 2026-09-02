package com.puppycoder.relay

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
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
import com.puppycoder.relay.data.SshTunnelConfig
import com.puppycoder.relay.data.SshConnection
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.TunnelRouteRule
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HopChainEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun multiHopTunnelShowsChainInCardAndEditor() {
        val profile = SshTunnelProfile(
            id = "chain-computer",
            name = "Office chain",
            hops = listOf(
                SshTunnelConfig(host = "gateway.example", connectionId = "gateway"),
                SshTunnelConfig(host = "bastion.internal", connectionId = "bastion"),
            ),
            routes = listOf(TunnelRouteRule("127.0.0.1", 4310)),
        )
        val repository = (compose.activity.application as PuppyCoderApplication).repository
        runBlocking {
            repository.saveSshConnection(SshConnection("gateway", "Office gateway", SshTunnelConfig(host = "gateway.example", username = "entry", password = "secret")))
            repository.saveSshConnection(SshConnection("bastion", "VPN bastion", SshTunnelConfig(host = "bastion.internal", username = "puppy", password = "secret")))
            repository.saveTunnel(profile)
        }

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Edit SSH computer ${profile.name}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithText("2 hops").fetchSemanticsNodes().isNotEmpty())
        capture("hop-card")

        compose.onNodeWithContentDescription("Edit SSH computer ${profile.name}").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Route").fetchSemanticsNodes().isNotEmpty()
        }
        capture("hop-editor")

        assertTrue(compose.onAllNodesWithText("gateway.example").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("bastion.internal").fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithContentDescription("SSH computer editor fields")
            .performScrollToNode(hasContentDescription("Hop 2", substring = true))
        compose.onNodeWithContentDescription("Hop 2 · final VPN bastion · bastion.internal:22").performClick()
        compose.onNodeWithText("Change SSH connection").fetchSemanticsNode()
        capture("hop-expanded")

        compose.onNodeWithContentDescription("SSH computer editor fields")
            .performScrollToNode(hasText("Add jump host"))
        compose.onNodeWithContentDescription("Add jump host").performClick()
        compose.waitForIdle()
        capture("hop-added")

        compose.onNodeWithContentDescription("SSH computer editor fields")
            .performScrollToNode(hasText("Edit computer"))
        compose.onNodeWithContentDescription("Close").performClick()
        runBlocking { repository.deleteTunnel(profile.id) }
    }

    private fun capture(name: String) {
        val sheet = compose.onAllNodesWithContentDescription("SSH computer editor fields")
            .fetchSemanticsNodes()
            .isNotEmpty()
        val node = if (sheet) {
            compose.onNodeWithContentDescription("SSH computer editor fields")
        } else {
            compose.onRoot()
        }
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.filesDir, "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
