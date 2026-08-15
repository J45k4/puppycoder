package com.puppycoder.relay

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.puppycoder.relay.data.SshTunnelConfig
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.TunnelRouteRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComputerEditingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sshComputerCanBeOpenedForEditingWithExistingValues() {
        val profile = SshTunnelProfile(
            id = "editable-computer",
            name = "Editable workstation",
            ssh = SshTunnelConfig(
                host = "workstation.example",
                username = "puppy",
                password = "secret",
            ),
            routes = listOf(TunnelRouteRule("127.0.0.1", 4310)),
            priority = 175,
        )
        val repository = (compose.activity.application as PuppyCoderApplication).repository
        runBlocking { repository.saveTunnel(profile) }

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Edit SSH computer ${profile.name}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Edit SSH computer ${profile.name}").performClick()

        compose.onNodeWithText("Edit computer").fetchSemanticsNode()
        check(compose.onAllNodesWithText(profile.name).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText(profile.ssh.host).fetchSemanticsNode()
        compose.onNodeWithText(profile.priority.toString()).fetchSemanticsNode()

        compose.onNodeWithContentDescription("Close").performClick()
        runBlocking { repository.deleteTunnel(profile.id) }
    }
}
