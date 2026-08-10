package com.puppycoder.relay.data

import java.util.UUID

enum class ConversationState {
    IDLE,
    SENDING,
    WORKING,
    OFFLINE,
    FAILED,
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

enum class DeliveryState {
    QUEUED,
    SENDING,
    STREAMING,
    DELIVERED,
    FAILED,
    DELIVERY_UNCERTAIN,
    STOPPED,
}

enum class ToolActivityState {
    RUNNING,
    COMPLETED,
    FAILED,
}

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val computerId: String,
    val title: String,
    val workspace: String,
    val remoteConversationId: String? = null,
    val modelId: String? = null,
    val modelProviderId: String? = null,
    val modelDisplayName: String? = null,
    val state: ConversationState = ConversationState.IDLE,
    val lastMessagePreview: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class AgentModel(
    val modelId: String,
    val displayName: String,
    val providerId: String? = null,
    val providerName: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
) {
    val key: String get() = listOfNotNull(providerId, modelId).joinToString("/")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val body: String,
    val deliveryState: DeliveryState,
    val remoteMessageId: String? = null,
    val remoteTurnId: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class ToolActivity(
    val id: String,
    val conversationId: String,
    val messageId: String?,
    val title: String,
    val detail: String,
    val state: ToolActivityState,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class ChatListItem(
    val conversation: Conversation,
    val computer: RelayServer,
)

data class RemoteConversationSummary(
    val remoteId: String,
    val title: String,
    val workspace: String,
    val preview: String = "",
    val modelId: String? = null,
    val modelProviderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RemoteChatMessage(
    val remoteId: String,
    val role: MessageRole,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val activities: List<RemoteChatActivity> = emptyList(),
)

data class RemoteChatActivity(
    val remoteId: String,
    val title: String,
    val detail: String = "",
    val failed: Boolean = false,
    val createdAt: Long,
    val replacesMessageRemoteId: String? = null,
)

data class RemoteChatSyncReport(
    val conversationsSeen: Int,
    val failures: List<String> = emptyList(),
)

sealed interface AgentConversationEvent {
    data class Accepted(
        val remoteConversationId: String,
        val remoteTurnId: String? = null,
        val remoteMessageId: String? = null,
    ) : AgentConversationEvent

    data object AgentWorking : AgentConversationEvent
    data class AssistantDelta(val text: String, val remoteMessageId: String? = null) : AgentConversationEvent
    data class ThinkingDelta(val id: String, val text: String) : AgentConversationEvent
    data class ToolStarted(val id: String, val title: String, val detail: String = "") : AgentConversationEvent
    data class ToolCompleted(
        val id: String,
        val title: String,
        val detail: String = "",
        val failed: Boolean = false,
    ) : AgentConversationEvent
    data object Completed : AgentConversationEvent
    data object Stopped : AgentConversationEvent
    data class Failed(val message: String, val deliveryWasAccepted: Boolean) : AgentConversationEvent
}

data class SendMessageRequest(
    val conversationId: String,
    val remoteConversationId: String?,
    val clientMessageId: String,
    val workspace: String,
    val text: String,
    val modelId: String? = null,
    val modelProviderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

interface AgentConversationClient {
    suspend fun send(
        computer: RelayServer,
        request: SendMessageRequest,
        onEvent: (AgentConversationEvent) -> Unit,
    )

    suspend fun stop(computer: RelayServer, conversationId: String): RemoteResult<Unit>
    suspend fun check(computer: RelayServer): RemoteResult<RemoteCheck>
    suspend fun listModels(computer: RelayServer): RemoteResult<List<AgentModel>>
    suspend fun listConversations(computer: RelayServer): RemoteResult<List<RemoteConversationSummary>>
    suspend fun loadConversation(
        computer: RelayServer,
        remoteConversationId: String,
    ): RemoteResult<List<RemoteChatMessage>>
    suspend fun testTunnel(
        profile: SshTunnelProfile,
        computerEndpoints: List<String>,
    ): RemoteResult<SshTunnelTest>
    suspend fun discoverServers(profile: SshTunnelProfile): RemoteResult<List<DiscoveredAgentServer>>
    fun setTunnelProfiles(profiles: List<SshTunnelProfile>)
    fun closeTunnelProfile(profileId: String)
    fun close()
}
