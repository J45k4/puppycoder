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
class LiveCodexConversationTest {
    @Test
    fun twoTurnsReuseOneRemoteThread() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("liveCodex") != "true") return@runBlocking
        val workspace = requireNotNull(arguments.getString("liveWorkspace")?.takeIf(String::isNotBlank)) {
            "Pass the app-server workspace as the liveWorkspace instrumentation argument"
        }

        val client = ConversationRemoteClient()
        val computer = RelayServer(
            id = "live-codex",
            name = "Codex",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = workspace,
            routeMode = ServerRouteMode.DIRECT,
        )
        try {
            val models = withTimeout(30_000) { client.listModels(computer) }
            assertTrue("Codex model discovery failed: $models", models is RemoteResult.Success && models.value.isNotEmpty())
            val selectedModel = (models as RemoteResult.Success).value.firstOrNull(AgentModel::isDefault)
                ?: models.value.first()
            val firstEvents = Collections.synchronizedList(mutableListOf<AgentConversationEvent>())
            withTimeout(120_000) {
                client.send(
                    computer,
                    SendMessageRequest(
                        conversationId = "local-live-chat",
                        remoteConversationId = null,
                        clientMessageId = UUID.randomUUID().toString(),
                        workspace = computer.workspace,
                        text = "Reply with exactly PUPPY_ONE and nothing else.",
                        modelId = selectedModel.modelId,
                    ),
                    firstEvents::add,
                )
            }
            val firstAccepted = firstEvents.filterIsInstance<AgentConversationEvent.Accepted>().single()
            assertTrue(firstEvents.filterIsInstance<AgentConversationEvent.AssistantDelta>().joinToString("") { it.text }.contains("PUPPY_ONE"))
            assertTrue(firstEvents.last() is AgentConversationEvent.Completed)

            val secondEvents = Collections.synchronizedList(mutableListOf<AgentConversationEvent>())
            withTimeout(120_000) {
                client.send(
                    computer,
                    SendMessageRequest(
                        conversationId = "local-live-chat",
                        remoteConversationId = firstAccepted.remoteConversationId,
                        clientMessageId = UUID.randomUUID().toString(),
                        workspace = computer.workspace,
                        text = "Reply with exactly PUPPY_TWO and nothing else.",
                    ),
                    secondEvents::add,
                )
            }
            val secondAccepted = secondEvents.filterIsInstance<AgentConversationEvent.Accepted>().single()
            assertEquals(firstAccepted.remoteConversationId, secondAccepted.remoteConversationId)
            assertTrue(secondEvents.filterIsInstance<AgentConversationEvent.AssistantDelta>().joinToString("") { it.text }.contains("PUPPY_TWO"))
            assertTrue(secondEvents.last() is AgentConversationEvent.Completed)

            val conversations = withTimeout(30_000) { client.listConversations(computer) }
            assertTrue(
                "Created Codex thread was not listed: $conversations",
                conversations is RemoteResult.Success &&
                    conversations.value.any { it.remoteId == firstAccepted.remoteConversationId },
            )
            val history = withTimeout(30_000) {
                client.loadConversation(computer, firstAccepted.remoteConversationId)
            }
            assertTrue("Codex history failed: $history", history is RemoteResult.Success)
            val historyText = (history as RemoteResult.Success).value.joinToString("\n") { it.body }
            assertTrue(historyText.contains("PUPPY_ONE"))
            assertTrue(historyText.contains("PUPPY_TWO"))
        } finally {
            client.close()
        }
    }
}
