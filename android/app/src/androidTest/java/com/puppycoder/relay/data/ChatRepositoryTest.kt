package com.puppycoder.relay.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: PuppyCoderDatabase
    private lateinit var repository: ChatRepository
    private lateinit var client: FakeConversationClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PuppyCoderDatabase::class.java).build()
        client = FakeConversationClient()
        repository = ChatRepository(context, database.chatDao(), client, SecretStore())
    }

    @Test
    fun imageOnlyMessageIsCopiedPersistedAndIncludedInDelivery() = runBlocking {
        val computer = RelayServer(
            id = "computer-1",
            name = "Codex computer",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = "/workspace",
            routeMode = ServerRouteMode.DIRECT,
        )
        repository.saveComputer(computer)
        val conversationId = repository.createConversation(computer.id, computer.workspace)
        val source = File(context.cacheDir, "attachment-source.png")
        Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.MAGENTA)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

        val messageId = repository.sendMessage(
            conversationId,
            text = "",
            imageUris = listOf(Uri.fromFile(source).toString()),
        )
        val optimistic = repository.messages(conversationId).first { messages ->
            messages.singleOrNull()?.images?.size == 1
        }.single()

        assertEquals(messageId, optimistic.id)
        assertEquals("", optimistic.body)
        assertEquals(32, optimistic.images.single().width)
        assertEquals(24, optimistic.images.single().height)
        assertTrue(File(optimistic.images.single().filePath).isFile)
        assertTrue(optimistic.images.single().filePath != source.absolutePath)

        client.release.complete(Unit)
        withTimeout(5_000) {
            repository.messages(conversationId).first { messages ->
                messages.firstOrNull { it.id == messageId }?.deliveryState == DeliveryState.DELIVERED
            }
        }
        assertEquals("", client.lastRequest?.text)
        assertEquals(1, client.lastRequest?.images?.size)
        source.delete()
        Unit
    }

    @After
    fun tearDown() {
        runBlocking { repository.shutdown() }
        database.close()
    }

    @Test
    fun optimisticMessageIsStoredBeforeRemoteAcceptanceThenStreamsReply() = runBlocking {
        val computer = RelayServer(
            id = "computer-1",
            name = "Codex computer",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = "/workspace",
            routeMode = ServerRouteMode.DIRECT,
        )
        repository.saveComputer(computer)
        val conversationId = repository.createConversation(computer.id, computer.workspace)
        repository.selectModel(
            conversationId,
            AgentModel(modelId = "gpt-test", displayName = "GPT Test"),
        )

        val messageId = repository.sendMessage(conversationId, "Hello agent")
        val optimistic = repository.messages(conversationId).first { it.isNotEmpty() }.single()
        assertEquals(messageId, optimistic.id)
        assertEquals("Hello agent", optimistic.body)
        assertTrue(optimistic.deliveryState == DeliveryState.QUEUED || optimistic.deliveryState == DeliveryState.SENDING)

        client.release.complete(Unit)
        val completed = withTimeout(5_000) {
            repository.messages(conversationId).first { messages ->
                messages.any { it.role == MessageRole.ASSISTANT && it.deliveryState == DeliveryState.DELIVERED }
            }
        }
        assertEquals("Hi from the agent", completed.first { it.role == MessageRole.ASSISTANT }.body)
        assertEquals(DeliveryState.DELIVERED, completed.first { it.role == MessageRole.USER }.deliveryState)
        assertEquals("gpt-test", client.lastRequest?.modelId)
        assertEquals("GPT Test", repository.conversation(conversationId).first()?.modelDisplayName)
    }

    @Test
    fun groupsReasoningToolsAndProgressWhileKeepingFinalResponseSeparate() = runBlocking {
        val computer = RelayServer(
            id = "computer-1",
            name = "Codex computer",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = "/workspace",
            routeMode = ServerRouteMode.DIRECT,
        )
        repository.saveComputer(computer)
        val conversationId = repository.createConversation(computer.id, computer.workspace)
        client.sendEvents = listOf(
            AgentConversationEvent.Accepted("thread-1", "turn-1", "user-remote"),
            AgentConversationEvent.AgentWorking,
            AgentConversationEvent.ThinkingDelta("reasoning-1", "Inspecting the project"),
            AgentConversationEvent.ToolCompleted("reasoning-1", "Thinking"),
            AgentConversationEvent.ToolStarted("shell-1", "Shell", "rg --files"),
            AgentConversationEvent.ToolCompleted("shell-1", "Shell"),
            AgentConversationEvent.AssistantDelta("I found the relevant files.", "progress-1"),
            AgentConversationEvent.AssistantDelta("The final response stays visible.", "final-1"),
            AgentConversationEvent.Completed,
        )

        repository.sendMessage(conversationId, "Inspect it")
        client.release.complete(Unit)

        val messages = withTimeout(5_000) {
            repository.messages(conversationId).first { current ->
                current.any { it.role == MessageRole.ASSISTANT && it.deliveryState == DeliveryState.DELIVERED }
            }
        }
        val activities = withTimeout(5_000) {
            repository.tools(conversationId).first { current ->
                current.size == 3 && current.all { it.state == ToolActivityState.COMPLETED }
            }
        }

        assertEquals("The final response stays visible.", messages.first { it.role == MessageRole.ASSISTANT }.body)
        assertEquals(setOf("Thinking", "Shell", "Agent update"), activities.map(ToolActivity::title).toSet())
        assertEquals("Inspecting the project", activities.first { it.title == "Thinking" }.detail)
        assertEquals("rg --files", activities.first { it.title == "Shell" }.detail)
        assertEquals("I found the relevant files.", activities.first { it.title == "Agent update" }.detail)
    }

    @Test
    fun routesCanBeAddedAndRemovedFromAnExistingTunnel() = runBlocking {
        val tunnel = SshTunnelProfile(
            id = "tunnel-1",
            name = "Gateway",
            ssh = SshTunnelConfig(host = "gateway.example", username = "puppy", password = "secret"),
            routes = listOf(TunnelRouteRule("127.0.0.1", 4310)),
        )
        repository.saveTunnel(tunnel)

        repository.addTunnelRoute(tunnel.id, TunnelRouteRule("*.internal", 4096))
        val withNewRoute = repository.tunnels.first { it.firstOrNull()?.routes?.size == 2 }.single()
        assertEquals(TunnelRouteRule("*.internal", 4096), withNewRoute.routes.last())
        assertTrue(
            runCatching {
                repository.addTunnelRoute(tunnel.id, TunnelRouteRule("*.INTERNAL", 4096))
            }.isFailure,
        )

        repository.deleteTunnelRoute(tunnel.id, TunnelRouteRule("*.internal", 4096))
        val withOneRoute = repository.tunnels.first { it.firstOrNull()?.routes?.size == 1 }.single()
        assertEquals(listOf(TunnelRouteRule("127.0.0.1", 4310)), withOneRoute.routes)
        assertTrue(
            runCatching { repository.deleteTunnelRoute(tunnel.id, withOneRoute.routes.single()) }.isFailure,
        )
    }

    @Test
    fun editingSshComputerPreservesItsIdentityAndRefreshesTheTunnel() = runBlocking {
        val original = SshTunnelProfile(
            id = "tunnel-edit",
            name = "Workstation",
            ssh = SshTunnelConfig(
                host = "old.example",
                username = "puppy",
                password = "old-secret",
            ),
            routes = listOf(TunnelRouteRule("127.0.0.1", 4310)),
            priority = 100,
        )
        repository.saveTunnel(original)

        repository.saveTunnel(
            original.copy(
                name = "Office workstation",
                ssh = original.ssh.copy(host = "new.example", password = "new-secret"),
                routes = listOf(TunnelRouteRule("agent.internal", 4096)),
                priority = 250,
            ),
        )

        val edited = repository.tunnels.first { it.singleOrNull()?.name == "Office workstation" }.single()
        assertEquals(original.id, edited.id)
        assertEquals("new.example", edited.ssh.host)
        assertEquals("new-secret", edited.ssh.password)
        assertEquals(listOf(TunnelRouteRule("agent.internal", 4096)), edited.routes)
        assertEquals(250, edited.priority)
        assertEquals(listOf(original.id), client.closedTunnelProfiles)
    }

    @Test
    fun remoteChatsAndHistoryAreImportedWithoutDuplicates() = runBlocking {
        val computer = RelayServer(
            id = "computer-1",
            name = "Codex computer",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = "/workspace",
            routeMode = ServerRouteMode.DIRECT,
        )
        repository.saveComputer(computer)
        client.remoteConversations = listOf(
            RemoteConversationSummary(
                remoteId = "thread-remote",
                title = "Existing server chat",
                workspace = "/remote/workspace",
                preview = "Hello from the server",
                modelId = "gpt-test",
                createdAt = 1_000,
                updatedAt = 2_000,
            ),
        )
        client.remoteMessages = listOf(
            RemoteChatMessage("user-remote", MessageRole.USER, "Hello", 1_100),
            RemoteChatMessage("assistant-remote", MessageRole.ASSISTANT, "Hi there", 1_200),
        )

        assertEquals(1, repository.syncRemoteChats().conversationsSeen)
        assertEquals(1, repository.syncRemoteChats().conversationsSeen)
        val importedChat = repository.chats.first { it.size == 1 }.single().conversation
        assertEquals("thread-remote", importedChat.remoteConversationId)
        assertEquals("Existing server chat", importedChat.title)
        assertEquals("/remote/workspace", importedChat.workspace)

        assertEquals(2, (repository.syncConversationHistory(importedChat.id) as RemoteResult.Success).value)
        assertEquals(0, (repository.syncConversationHistory(importedChat.id) as RemoteResult.Success).value)
        val messages = repository.messages(importedChat.id).first { it.size == 2 }
        assertEquals(listOf("Hello", "Hi there"), messages.map(ChatMessage::body))
        assertTrue(messages.all { it.deliveryState == DeliveryState.DELIVERED })
        assertEquals(importedChat.id, repository.searchChats("Existing server").first().single().conversation.id)
        assertEquals(importedChat.id, repository.searchChats("Hi there").first().single().conversation.id)
        assertTrue(repository.searchChats("does not exist").first().isEmpty())
    }

    @Test
    fun existingHistoryIsBackfilledIntoActivityBlock() = runBlocking {
        val computer = RelayServer(
            id = "computer-1",
            name = "Codex computer",
            kind = ServerKind.CODEX,
            endpoint = "ws://127.0.0.1:4310",
            workspace = "/workspace",
            routeMode = ServerRouteMode.DIRECT,
        )
        repository.saveComputer(computer)
        client.remoteConversations = listOf(
            RemoteConversationSummary(
                remoteId = "thread-remote",
                title = "Existing chat",
                workspace = "/workspace",
                createdAt = 1_000,
                updatedAt = 2_000,
            ),
        )
        client.remoteMessages = listOf(
            RemoteChatMessage("user-remote", MessageRole.USER, "Inspect it", 1_100),
            RemoteChatMessage("progress-remote", MessageRole.ASSISTANT, "I am checking.", 1_200),
            RemoteChatMessage("final-remote", MessageRole.ASSISTANT, "Everything looks good.", 1_300),
        )
        repository.syncRemoteChats()
        val chat = repository.chats.first { it.size == 1 }.single().conversation
        assertEquals(3, (repository.syncConversationHistory(chat.id) as RemoteResult.Success).value)

        client.remoteMessages = listOf(
            RemoteChatMessage("user-remote", MessageRole.USER, "Inspect it", 1_100),
            RemoteChatMessage(
                remoteId = "final-remote",
                role = MessageRole.ASSISTANT,
                body = "Everything looks good.",
                createdAt = 1_300,
                activities = listOf(
                    RemoteChatActivity(
                        remoteId = "agent-update:progress-remote",
                        title = "Agent update",
                        detail = "I am checking.",
                        createdAt = 1_200,
                        replacesMessageRemoteId = "progress-remote",
                    ),
                    RemoteChatActivity(
                        remoteId = "reasoning-remote",
                        title = "Thinking",
                        detail = "Checked the relevant files.",
                        createdAt = 1_250,
                    ),
                ),
            ),
        )

        assertEquals(0, (repository.syncConversationHistory(chat.id) as RemoteResult.Success).value)
        val messages = repository.messages(chat.id).first { it.size == 2 }
        val activities = repository.tools(chat.id).first { it.size == 2 }

        assertEquals(listOf("Inspect it", "Everything looks good."), messages.map(ChatMessage::body))
        assertEquals(setOf("Agent update", "Thinking"), activities.map(ToolActivity::title).toSet())
        assertTrue(activities.all { it.messageId == messages.last().id })
    }

    @Test
    fun discoveredAgentServicesAreAddedAutomaticallyWithoutDuplicates() = runBlocking {
        val tunnel = SshTunnelProfile(
            id = "ssh-computer",
            name = "Workstation",
            ssh = SshTunnelConfig(host = "workstation.example", username = "puppy", password = "secret"),
            routes = listOf(TunnelRouteRule("127.0.0.1", 4310)),
        )
        repository.saveTunnel(tunnel)
        val found = listOf(
            DiscoveredAgentServer(
                kind = ServerKind.CODEX,
                endpoint = "ws://127.0.0.1:4310",
                suggestedName = "Workstation · Codex",
                suggestedWorkspace = "/home/puppy",
                version = "Ready",
                latencyMs = 12,
            ),
        )

        assertEquals(1, repository.saveDiscoveredServers(tunnel.id, found))
        assertEquals(0, repository.saveDiscoveredServers(tunnel.id, found))
        val service = repository.computers.first { it.size == 1 }.single()
        assertEquals(ServerKind.CODEX, service.kind)
        assertEquals(ServerRouteMode.TUNNEL, service.routeMode)
        assertEquals(tunnel.id, service.tunnelProfileId)
        assertEquals("/home/puppy", service.workspace)
    }
}

private class FakeConversationClient : AgentConversationClient {
    val release = CompletableDeferred<Unit>()
    @Volatile var lastRequest: SendMessageRequest? = null
    var sendEvents: List<AgentConversationEvent>? = null
    var remoteConversations: List<RemoteConversationSummary> = emptyList()
    var remoteMessages: List<RemoteChatMessage> = emptyList()
    val closedTunnelProfiles = mutableListOf<String>()

    override suspend fun send(
        computer: RelayServer,
        request: SendMessageRequest,
        onEvent: (AgentConversationEvent) -> Unit,
    ) {
        lastRequest = request
        release.await()
        val events = sendEvents ?: listOf(
            AgentConversationEvent.Accepted("thread-1", "turn-1", request.clientMessageId),
            AgentConversationEvent.AgentWorking,
            AgentConversationEvent.AssistantDelta("Hi from the agent", "assistant-1"),
            AgentConversationEvent.Completed,
        )
        events.forEach(onEvent)
    }

    override suspend fun stop(computer: RelayServer, conversationId: String) = RemoteResult.Success(Unit)
    override suspend fun check(computer: RelayServer) = RemoteResult.Success(RemoteCheck("test", 1, "Direct"))
    override suspend fun listModels(computer: RelayServer) = RemoteResult.Success(
        listOf(AgentModel("gpt-test", "GPT Test")),
    )
    override suspend fun listConversations(computer: RelayServer) = RemoteResult.Success(remoteConversations)
    override suspend fun loadConversation(computer: RelayServer, remoteConversationId: String) =
        RemoteResult.Success(remoteMessages)
    override suspend fun testTunnel(profile: SshTunnelProfile, computerEndpoints: List<String>) =
        RemoteResult.Success(SshTunnelTest("ok", 1))
    override suspend fun discoverServers(profile: SshTunnelProfile) =
        RemoteResult.Success(emptyList<DiscoveredAgentServer>())
    override fun setTunnelProfiles(profiles: List<SshTunnelProfile>) = Unit
    override fun closeTunnelProfile(profileId: String) {
        closedTunnelProfiles += profileId
    }
    override fun close() = Unit
}
