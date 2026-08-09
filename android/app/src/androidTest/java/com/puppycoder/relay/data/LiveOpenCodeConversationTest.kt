package com.puppycoder.relay.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiveOpenCodeConversationTest {
    @Test
    fun twoTurnsReuseOneRemoteSession() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("liveOpenCode") != "true") return@runBlocking
        val workspace = requireNotNull(arguments.getString("liveWorkspace")?.takeIf(String::isNotBlank)) {
            "Pass the OpenCode workspace as the liveWorkspace instrumentation argument"
        }

        val client = ConversationRemoteClient()
        val computer = RelayServer(
            id = "live-opencode",
            name = "OpenCode",
            kind = ServerKind.OPENCODE,
            endpoint = "http://127.0.0.1:4099",
            workspace = workspace,
            routeMode = ServerRouteMode.DIRECT,
        )
        try {
            val models = withTimeout(45_000) { client.listModels(computer) }
            assertTrue("OpenCode model discovery failed: $models", models is RemoteResult.Success && models.value.isNotEmpty())
            val selectedModel = (models as RemoteResult.Success).value.firstOrNull(AgentModel::isDefault)
                ?: models.value.first()
            val firstEvents = Collections.synchronizedList(mutableListOf<AgentConversationEvent>())
            withTimeout(120_000) {
                client.send(
                    computer,
                    SendMessageRequest(
                        conversationId = "local-live-opencode-chat",
                        remoteConversationId = null,
                        clientMessageId = UUID.randomUUID().toString(),
                        workspace = computer.workspace,
                        text = "Reply with exactly PUPPY_OPEN_ONE and nothing else.",
                        modelId = selectedModel.modelId,
                        modelProviderId = selectedModel.providerId,
                    ),
                    firstEvents::add,
                )
            }
            val firstAccepted = firstEvents.filterIsInstance<AgentConversationEvent.Accepted>().single()
            assertTrue(firstEvents.filterIsInstance<AgentConversationEvent.AssistantDelta>().joinToString("") { it.text }.contains("PUPPY_OPEN_ONE"))
            assertTrue(firstEvents.last() is AgentConversationEvent.Completed)

            val secondEvents = Collections.synchronizedList(mutableListOf<AgentConversationEvent>())
            withTimeout(120_000) {
                client.send(
                    computer,
                    SendMessageRequest(
                        conversationId = "local-live-opencode-chat",
                        remoteConversationId = firstAccepted.remoteConversationId,
                        clientMessageId = UUID.randomUUID().toString(),
                        workspace = computer.workspace,
                        text = "Reply with exactly PUPPY_OPEN_TWO and nothing else.",
                    ),
                    secondEvents::add,
                )
            }
            val secondAccepted = secondEvents.filterIsInstance<AgentConversationEvent.Accepted>().single()
            assertEquals(firstAccepted.remoteConversationId, secondAccepted.remoteConversationId)
            assertTrue(secondEvents.filterIsInstance<AgentConversationEvent.AssistantDelta>().joinToString("") { it.text }.contains("PUPPY_OPEN_TWO"))
            assertTrue(secondEvents.last() is AgentConversationEvent.Completed)

            val conversations = withTimeout(30_000) { client.listConversations(computer) }
            assertTrue(
                "Created OpenCode session was not listed: $conversations",
                conversations is RemoteResult.Success &&
                    conversations.value.any { it.remoteId == firstAccepted.remoteConversationId },
            )
            val history = withTimeout(30_000) {
                client.loadConversation(computer, firstAccepted.remoteConversationId)
            }
            assertTrue("OpenCode history failed: $history", history is RemoteResult.Success)
            val historyText = (history as RemoteResult.Success).value.joinToString("\n") { it.body }
            assertTrue(historyText.contains("PUPPY_OPEN_ONE"))
            assertTrue(historyText.contains("PUPPY_OPEN_TWO"))
        } finally {
            client.close()
        }
    }
}
