package com.puppycoder.relay.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ChatRepository(
    private val context: Context,
    private val dao: ChatDao,
    private val client: AgentConversationClient,
    private val secretStore: SecretStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conversationLocks = ConcurrentHashMap<String, Mutex>()
    private val remoteSyncMutex = Mutex()
    private val imageStore = MessageImageStore(context)

    val computers: Flow<List<RelayServer>> = dao.observeComputers().map { entities ->
        entities.map { it.toModel(secretStore) }
    }

    val tunnels: Flow<List<SshTunnelProfile>> = dao.observeTunnels().map { tunnels ->
        tunnels.map { it.toModel(secretStore) }
    }

    val chats: Flow<List<ChatListItem>> = chatListItems(dao.observeConversations())

    fun searchChats(query: String): Flow<List<ChatListItem>> = chatListItems(
        if (query.isBlank()) dao.observeConversations() else dao.searchConversations(query.trim()),
    )

    private fun chatListItems(
        conversationsFlow: Flow<List<ConversationEntity>>,
    ): Flow<List<ChatListItem>> = combine(
        conversationsFlow,
        computers,
    ) { conversations, computers ->
        val byId = computers.associateBy(RelayServer::id)
        conversations.mapNotNull { entity ->
            byId[entity.computerId]?.let { ChatListItem(entity.toModel(), it) }
        }
    }

    suspend fun initialize() {
        client.setTunnelProfiles(dao.getTunnels().map { it.toModel(secretStore) })
        scope.launch { tunnels.collect(client::setTunnelProfiles) }
        val now = System.currentTimeMillis()
        dao.recoverSendingMessages(now)
        dao.recoverStreamingMessages(now)
        dao.conversationsWithQueuedMessages().forEach(::scheduleOutbox)
    }

    suspend fun syncRemoteChats(): RemoteChatSyncReport = remoteSyncMutex.withLock {
        var seen = 0
        val failures = mutableListOf<String>()
        for (entity in dao.getComputers()) {
            val computer = entity.toModel(secretStore)
            when (val result = client.listConversations(computer)) {
                is RemoteResult.Success -> {
                    seen += result.value.size
                    result.value.forEach { remote -> upsertRemoteConversation(computer, remote) }
                }
                is RemoteResult.Error -> failures += "${computer.name}: ${result.message}"
            }
        }
        RemoteChatSyncReport(seen, failures)
    }

    suspend fun syncConversationHistory(conversationId: String): RemoteResult<Int> {
        val conversation = dao.getConversation(conversationId)
            ?: return RemoteResult.Error("Chat not found")
        val remoteId = conversation.remoteConversationId
            ?: return RemoteResult.Success(0)
        val computer = dao.getComputer(conversation.computerId)?.toModel(secretStore)
            ?: return RemoteResult.Error("Computer not found")
        return when (val result = client.loadConversation(computer, remoteId)) {
            is RemoteResult.Error -> result
            is RemoteResult.Success -> {
                val existingMessages = dao.getMessages(conversationId)
                val replacedRemoteIds = result.value
                    .flatMap(RemoteChatMessage::activities)
                    .mapNotNull(RemoteChatActivity::replacesMessageRemoteId)
                    .toSet()
                existingMessages
                    .filter { it.remoteMessageId in replacedRemoteIds }
                    .forEach { dao.deleteMessage(it.id) }

                val retainedMessages = existingMessages.filterNot { it.remoteMessageId in replacedRemoteIds }
                val existingByRemoteId = retainedMessages.mapNotNull { message ->
                    message.remoteMessageId?.let { it to message }
                }.toMap().toMutableMap()
                var importedCount = 0

                result.value.forEach { remote ->
                    val existing = existingByRemoteId[remote.remoteId]
                    val local = if (existing == null) {
                        ChatMessage(
                            id = UUID.nameUUIDFromBytes(
                                "remote-message:$conversationId:${remote.remoteId}".toByteArray(),
                            ).toString(),
                            conversationId = conversationId,
                            role = remote.role,
                            body = remote.body,
                            deliveryState = DeliveryState.DELIVERED,
                            remoteMessageId = remote.remoteId,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                        ).toEntity().also {
                            dao.insertMessagesIfMissing(listOf(it))
                            importedCount += 1
                            existingByRemoteId[remote.remoteId] = it
                        }
                    } else {
                        val updated = existing.copy(
                            role = remote.role.name,
                            body = remote.body,
                            deliveryState = DeliveryState.DELIVERED.name,
                            updatedAt = maxOf(existing.updatedAt, remote.updatedAt),
                        )
                        dao.updateMessage(updated)
                        updated
                    }

                    remote.activities.forEach { activity ->
                        dao.upsertTool(
                            ToolActivityEntity(
                                id = UUID.nameUUIDFromBytes(
                                    "remote-activity:$conversationId:${activity.remoteId}".toByteArray(),
                                ).toString(),
                                conversationId = conversationId,
                                messageId = local.id,
                                title = activity.title,
                                detail = activity.detail,
                                state = if (activity.failed) {
                                    ToolActivityState.FAILED.name
                                } else {
                                    ToolActivityState.COMPLETED.name
                                },
                                createdAt = activity.createdAt,
                                updatedAt = remote.updatedAt,
                            ),
                        )
                    }
                }
                result.value.lastOrNull { it.body.isNotBlank() }?.let { latest ->
                    val current = dao.getConversation(conversationId) ?: conversation
                    dao.updateConversation(
                        current.copy(
                            lastMessagePreview = latest.body.takeLast(120),
                            updatedAt = maxOf(current.updatedAt, latest.updatedAt),
                        ),
                    )
                }
                RemoteResult.Success(importedCount)
            }
        }
    }

    private suspend fun upsertRemoteConversation(
        computer: RelayServer,
        remote: RemoteConversationSummary,
    ) {
        val existing = dao.getConversationByRemoteId(computer.id, remote.remoteId)
        if (existing == null) {
            val createdAt = remote.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            dao.insertConversation(
                Conversation(
                    id = UUID.nameUUIDFromBytes(
                        "remote-chat:${computer.id}:${remote.remoteId}".toByteArray(),
                    ).toString(),
                    computerId = computer.id,
                    title = remote.title.ifBlank { "Imported chat" },
                    workspace = remote.workspace.ifBlank { computer.workspace },
                    remoteConversationId = remote.remoteId,
                    modelId = remote.modelId,
                    modelProviderId = remote.modelProviderId,
                    modelDisplayName = remote.modelId,
                    lastMessagePreview = remote.preview,
                    createdAt = createdAt,
                    updatedAt = remote.updatedAt.takeIf { it > 0 } ?: createdAt,
                ).toEntity(),
            )
        } else {
            dao.updateConversation(
                existing.copy(
                    title = remote.title.ifBlank { existing.title },
                    workspace = remote.workspace.ifBlank { existing.workspace },
                    modelId = existing.modelId ?: remote.modelId,
                    modelProviderId = existing.modelProviderId ?: remote.modelProviderId,
                    modelDisplayName = existing.modelDisplayName ?: remote.modelId,
                    lastMessagePreview = remote.preview.ifBlank { existing.lastMessagePreview },
                    createdAt = listOf(existing.createdAt, remote.createdAt)
                        .filter { it > 0 }
                        .minOrNull() ?: existing.createdAt,
                    updatedAt = maxOf(existing.updatedAt, remote.updatedAt),
                ),
            )
        }
    }

    fun conversation(id: String): Flow<Conversation?> = dao.observeConversation(id).map { it?.toModel() }

    fun messages(conversationId: String): Flow<List<ChatMessage>> = combine(
        dao.observeMessages(conversationId),
        dao.observeMessageImages(conversationId),
    ) { messages, images ->
        val imagesByMessage = images.map(MessageImageEntity::toModel).groupBy(MessageImage::messageId)
        messages.map { it.toModel(imagesByMessage[it.id].orEmpty()) }
    }

    fun tools(conversationId: String): Flow<List<ToolActivity>> =
        dao.observeTools(conversationId).map { list -> list.map(ToolActivityEntity::toModel) }

    suspend fun saveComputer(computer: RelayServer) {
        dao.upsertComputer(computer.toEntity(secretStore))
    }

    suspend fun deleteComputer(id: String): Result<Unit> = runCatching { dao.deleteComputer(id) }

    suspend fun saveTunnel(profile: SshTunnelProfile) {
        val replacingExisting = dao.getTunnels().any { it.profile.id == profile.id }
        val (entity, routes) = profile.toEntities(secretStore)
        dao.upsertTunnel(entity, routes)
        if (replacingExisting) client.closeTunnelProfile(profile.id)
        client.setTunnelProfiles(dao.getTunnels().map { it.toModel(secretStore) })
    }

    suspend fun addTunnelRoute(id: String, route: TunnelRouteRule) {
        val tunnel = dao.getTunnels().firstOrNull { it.profile.id == id }?.toModel(secretStore)
            ?: error("SSH tunnel not found")
        require(tunnel.routes.none { it.hostPattern.equals(route.hostPattern, ignoreCase = true) && it.port == route.port }) {
            "That SSH route already exists"
        }
        saveTunnel(tunnel.copy(routes = tunnel.routes + route.copy(hostPattern = route.hostPattern.lowercase())))
    }

    suspend fun deleteTunnelRoute(id: String, route: TunnelRouteRule) {
        val tunnel = dao.getTunnels().firstOrNull { it.profile.id == id }?.toModel(secretStore)
            ?: error("SSH tunnel not found")
        require(tunnel.routes.size > 1) { "A tunnel needs at least one route" }
        saveTunnel(tunnel.copy(routes = tunnel.routes.filterNot { it == route }))
    }

    suspend fun deleteTunnel(id: String) {
        client.closeTunnelProfile(id)
        dao.deleteTunnel(id)
    }

    suspend fun testTunnel(id: String): RemoteResult<SshTunnelTest> {
        val profile = dao.getTunnels().firstOrNull { it.profile.id == id }?.toModel(secretStore)
            ?: return RemoteResult.Error("SSH tunnel not found")
        val endpoints = dao.getComputers().map(ComputerEntity::endpoint)
        return client.testTunnel(profile, endpoints)
    }

    suspend fun discoverServers(id: String): RemoteResult<List<DiscoveredAgentServer>> {
        val profile = dao.getTunnels().firstOrNull { it.profile.id == id }?.toModel(secretStore)
            ?: return RemoteResult.Error("SSH tunnel not found")
        return client.discoverServers(profile)
    }

    suspend fun saveDiscoveredServers(
        tunnelId: String,
        results: List<DiscoveredAgentServer>,
    ): Int {
        val existing = dao.getComputers()
        var added = 0
        results.forEach { discovered ->
            val current = existing.firstOrNull { computer ->
                computer.kind == discovered.kind.name &&
                    computer.endpoint == discovered.endpoint &&
                    computer.tunnelProfileId == tunnelId
            }
            if (current == null) {
                saveComputer(
                    RelayServer(
                        id = UUID.nameUUIDFromBytes(
                            "discovered-server:$tunnelId:${discovered.kind}:${discovered.endpoint}".toByteArray(),
                        ).toString(),
                        name = discovered.suggestedName,
                        kind = discovered.kind,
                        endpoint = discovered.endpoint,
                        workspace = discovered.suggestedWorkspace,
                        routeMode = ServerRouteMode.TUNNEL,
                        tunnelProfileId = tunnelId,
                        allowDirectFallback = false,
                        state = if (discovered.requiresAuthentication) ConnectionState.OFFLINE else ConnectionState.ONLINE,
                        version = discovered.version,
                        latencyMs = discovered.latencyMs,
                    ),
                )
                added += 1
            } else {
                dao.upsertComputer(
                    current.copy(
                        connectionState = if (discovered.requiresAuthentication) {
                            ConnectionState.OFFLINE.name
                        } else {
                            ConnectionState.ONLINE.name
                        },
                        version = discovered.version,
                        latencyMs = discovered.latencyMs,
                    ),
                )
            }
        }
        return added
    }

    suspend fun checkComputer(id: String): RemoteResult<RemoteCheck> {
        val entity = dao.getComputer(id) ?: return RemoteResult.Error("Computer not found")
        val computer = entity.toModel(secretStore)
        val checking = computer.copy(state = ConnectionState.CHECKING)
        dao.upsertComputer(checking.toEntity(secretStore))
        return when (val result = client.check(computer)) {
            is RemoteResult.Success -> {
                dao.upsertComputer(
                    computer.copy(
                        state = ConnectionState.ONLINE,
                        version = result.value.version,
                        latencyMs = result.value.latencyMs,
                    ).toEntity(secretStore),
                )
                result
            }
            is RemoteResult.Error -> {
                dao.upsertComputer(computer.copy(state = ConnectionState.OFFLINE).toEntity(secretStore))
                result
            }
        }
    }

    suspend fun listModels(computerId: String): RemoteResult<List<AgentModel>> {
        val computer = dao.getComputer(computerId)?.toModel(secretStore)
            ?: return RemoteResult.Error("Computer not found")
        return client.listModels(computer)
    }

    suspend fun selectModel(conversationId: String, model: AgentModel?) {
        val conversation = dao.getConversation(conversationId) ?: error("Chat not found")
        require(conversation.state != ConversationState.SENDING.name && conversation.state != ConversationState.WORKING.name) {
            "Wait for the current response before changing models"
        }
        dao.updateConversation(
            conversation.copy(
                modelId = model?.modelId,
                modelProviderId = model?.providerId,
                modelDisplayName = model?.displayName,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun createConversation(computerId: String, workspace: String): String {
        val computer = dao.getComputer(computerId)?.toModel(secretStore)
            ?: error("Computer not found")
        val conversation = Conversation(
            computerId = computerId,
            title = "New chat",
            workspace = workspace.ifBlank { computer.workspace },
        )
        dao.insertConversation(conversation.toEntity())
        return conversation.id
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        imageUris: List<String> = emptyList(),
    ): String {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || imageUris.isNotEmpty()) { "Message cannot be empty" }
        val conversation = dao.getConversation(conversationId) ?: error("Chat not found")
        val now = System.currentTimeMillis()
        val message = ChatMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            body = trimmed,
            deliveryState = DeliveryState.QUEUED,
            createdAt = now,
            updatedAt = now,
        )
        val images = imageStore.import(message.id, imageUris)
        val fallback = if (images.size == 1) "Image" else "${images.size} images"
        val preview = trimmed.ifBlank { "📷 $fallback" }
        try {
            dao.insertOptimisticMessage(
                conversation.copy(
                    title = if (conversation.title == "New chat") preview.take(60) else conversation.title,
                    lastMessagePreview = preview.take(120),
                    state = ConversationState.SENDING.name,
                    updatedAt = now,
                ),
                message.toEntity(),
                images.map(MessageImage::toEntity),
            )
        } catch (error: Throwable) {
            images.forEach(imageStore::delete)
            throw error
        }
        scheduleOutbox(conversationId)
        scope.launch { drainOutbox(conversationId) }
        return message.id
    }

    suspend fun retryMessage(messageId: String) {
        val message = dao.getMessage(messageId) ?: return
        val now = System.currentTimeMillis()
        dao.updateMessage(
            message.copy(
                deliveryState = DeliveryState.QUEUED.name,
                errorMessage = null,
                updatedAt = now,
            ),
        )
        dao.getConversation(message.conversationId)?.let {
            dao.updateConversation(it.copy(state = ConversationState.SENDING.name, updatedAt = now))
        }
        scheduleOutbox(message.conversationId)
        scope.launch { drainOutbox(message.conversationId) }
    }

    suspend fun removeQueuedMessage(messageId: String) {
        val message = dao.getMessage(messageId) ?: return
        if (message.deliveryState == DeliveryState.QUEUED.name ||
            message.deliveryState == DeliveryState.FAILED.name
        ) {
            val images = dao.getMessageImages(messageId).map(MessageImageEntity::toModel)
            dao.deleteMessage(messageId)
            images.forEach(imageStore::delete)
        }
    }

    suspend fun stop(conversationId: String): RemoteResult<Unit> {
        val conversation = dao.getConversation(conversationId)
            ?: return RemoteResult.Error("Chat not found")
        val computer = dao.getComputer(conversation.computerId)?.toModel(secretStore)
            ?: return RemoteResult.Error("Computer not found")
        return when (val result = client.stop(computer, conversationId)) {
            is RemoteResult.Success -> {
                val now = System.currentTimeMillis()
                dao.updateConversation(conversation.copy(state = ConversationState.IDLE.name, updatedAt = now))
                result
            }
            is RemoteResult.Error -> result
        }
    }

    suspend fun drainOutbox(conversationId: String) {
        conversationLocks.getOrPut(conversationId) { Mutex() }.withLock {
            while (true) {
                val message = dao.nextQueuedMessage(conversationId) ?: break
                deliver(message)
                if (dao.getMessage(message.id)?.deliveryState == DeliveryState.FAILED.name) break
            }
        }
    }

    private suspend fun deliver(outgoing: ChatMessageEntity) {
        var conversation = dao.getConversation(outgoing.conversationId) ?: return
        val computer = dao.getComputer(conversation.computerId)?.toModel(secretStore) ?: return
        val now = System.currentTimeMillis()
        dao.updateMessage(
            outgoing.copy(
                deliveryState = DeliveryState.SENDING.name,
                errorMessage = null,
                updatedAt = now,
            ),
        )
        dao.updateConversation(conversation.copy(state = ConversationState.SENDING.name, updatedAt = now))

        val events = Channel<AgentConversationEvent>(Channel.UNLIMITED)
        val consumer = scope.launch {
            for (event in events) handleEvent(outgoing, event)
        }
        client.send(
            computer,
            SendMessageRequest(
                conversationId = conversation.id,
                remoteConversationId = conversation.remoteConversationId,
                clientMessageId = outgoing.id,
                workspace = conversation.workspace,
                text = outgoing.body,
                images = dao.getMessageImages(outgoing.id).map(MessageImageEntity::toModel),
                modelId = conversation.modelId,
                modelProviderId = conversation.modelProviderId,
                createdAt = outgoing.createdAt,
            ),
        ) { events.trySend(it) }
        events.close()
        consumer.join()
    }

    private suspend fun handleEvent(outgoing: ChatMessageEntity, event: AgentConversationEvent) {
        val now = System.currentTimeMillis()
        val conversation = dao.getConversation(outgoing.conversationId) ?: return
        val currentOutgoing = dao.getMessage(outgoing.id) ?: return
        val assistantId = "assistant-${outgoing.id}"
        when (event) {
            is AgentConversationEvent.Accepted -> {
                dao.updateMessage(
                    currentOutgoing.copy(
                        deliveryState = DeliveryState.DELIVERED.name,
                        remoteMessageId = event.remoteMessageId,
                        remoteTurnId = event.remoteTurnId,
                        updatedAt = now,
                    ),
                )
                dao.updateConversation(
                    conversation.copy(
                        remoteConversationId = event.remoteConversationId,
                        state = ConversationState.WORKING.name,
                        updatedAt = now,
                    ),
                )
            }
            AgentConversationEvent.AgentWorking -> {
                dao.updateConversation(conversation.copy(state = ConversationState.WORKING.name, updatedAt = now))
            }
            is AgentConversationEvent.AssistantDelta -> {
                val current = dao.getMessage(assistantId)
                if (current == null) {
                    dao.insertMessage(
                        ChatMessageEntity(
                            id = assistantId,
                            conversationId = outgoing.conversationId,
                            role = MessageRole.ASSISTANT.name,
                            body = event.text,
                            deliveryState = DeliveryState.STREAMING.name,
                            remoteMessageId = event.remoteMessageId,
                            remoteTurnId = currentOutgoing.remoteTurnId,
                            errorMessage = null,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else {
                    val startsNewSegment = !event.remoteMessageId.isNullOrBlank() &&
                        !current.remoteMessageId.isNullOrBlank() &&
                        event.remoteMessageId != current.remoteMessageId
                    if (startsNewSegment && current.body.isNotBlank()) {
                        dao.upsertTool(
                            ToolActivityEntity(
                                id = "$assistantId-message-${current.remoteMessageId}",
                                conversationId = outgoing.conversationId,
                                messageId = assistantId,
                                title = "Agent update",
                                detail = current.body,
                                state = ToolActivityState.COMPLETED.name,
                                createdAt = current.createdAt,
                                updatedAt = now,
                            ),
                        )
                    }
                    dao.updateMessage(
                        current.copy(
                            body = if (startsNewSegment) event.text else current.body + event.text,
                            deliveryState = DeliveryState.STREAMING.name,
                            remoteMessageId = event.remoteMessageId ?: current.remoteMessageId,
                            updatedAt = now,
                        ),
                    )
                }
                dao.updateConversation(
                    conversation.copy(
                        state = ConversationState.WORKING.name,
                        lastMessagePreview = (dao.getMessage(assistantId)?.body ?: event.text).takeLast(120),
                        updatedAt = now,
                    ),
                )
            }
            is AgentConversationEvent.ThinkingDelta -> {
                val id = "${outgoing.id}-${event.id}"
                val current = dao.getTool(id)
                dao.upsertTool(
                    ToolActivityEntity(
                        id = id,
                        conversationId = outgoing.conversationId,
                        messageId = assistantId,
                        title = "Thinking",
                        detail = current?.detail.orEmpty() + event.text,
                        state = ToolActivityState.RUNNING.name,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
            is AgentConversationEvent.ToolStarted -> {
                val id = "${outgoing.id}-${event.id}"
                val current = dao.getTool(id)
                dao.upsertTool(
                    ToolActivityEntity(
                        id = id,
                        conversationId = outgoing.conversationId,
                        messageId = assistantId,
                        title = event.title,
                        detail = event.detail.ifBlank { current?.detail.orEmpty() },
                        state = ToolActivityState.RUNNING.name,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
            is AgentConversationEvent.ToolCompleted -> {
                val id = "${outgoing.id}-${event.id}"
                val current = dao.getTool(id)
                dao.upsertTool(
                    ToolActivityEntity(
                        id = id,
                        conversationId = outgoing.conversationId,
                        messageId = assistantId,
                        title = event.title,
                        detail = event.detail.ifBlank { current?.detail.orEmpty() },
                        state = if (event.failed) ToolActivityState.FAILED.name else ToolActivityState.COMPLETED.name,
                        createdAt = current?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
            AgentConversationEvent.Completed -> {
                dao.getMessage(assistantId)?.let {
                    dao.updateMessage(it.copy(deliveryState = DeliveryState.DELIVERED.name, updatedAt = now))
                }
                dao.updateConversation(conversation.copy(state = ConversationState.IDLE.name, updatedAt = now))
            }
            AgentConversationEvent.Stopped -> {
                dao.getMessage(assistantId)?.let {
                    dao.updateMessage(it.copy(deliveryState = DeliveryState.STOPPED.name, updatedAt = now))
                }
                dao.updateConversation(conversation.copy(state = ConversationState.IDLE.name, updatedAt = now))
            }
            is AgentConversationEvent.Failed -> {
                if (event.deliveryWasAccepted) {
                    dao.getMessage(assistantId)?.let {
                        dao.updateMessage(
                            it.copy(
                                deliveryState = DeliveryState.DELIVERY_UNCERTAIN.name,
                                errorMessage = event.message,
                                updatedAt = now,
                            ),
                        )
                    }
                } else {
                    dao.updateMessage(
                        currentOutgoing.copy(
                            deliveryState = DeliveryState.FAILED.name,
                            errorMessage = event.message,
                            updatedAt = now,
                        ),
                    )
                }
                dao.updateConversation(conversation.copy(state = ConversationState.FAILED.name, updatedAt = now))
            }
        }
    }

    private fun scheduleOutbox(conversationId: String) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setInputData(Data.Builder().putString(OutboxWorker.CONVERSATION_ID, conversationId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "chat-outbox-$conversationId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun shutdown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        client.close()
    }
}
