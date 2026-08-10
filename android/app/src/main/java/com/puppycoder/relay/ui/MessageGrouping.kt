package com.puppycoder.relay.ui

import com.puppycoder.relay.data.ChatMessage
import com.puppycoder.relay.data.MessageRole
import com.puppycoder.relay.data.ToolActivity

internal data class ChatMessageGroup(
    val message: ChatMessage,
    val activities: List<ToolActivity>,
)

internal data class ArrangedChatMessages(
    val groups: List<ChatMessageGroup>,
    val unboundActivities: List<ToolActivity>,
)

internal fun arrangeMessageActivities(
    messages: List<ChatMessage>,
    activities: List<ToolActivity>,
): ArrangedChatMessages {
    val assistantMessageIds = messages
        .filter { it.role == MessageRole.ASSISTANT }
        .mapTo(mutableSetOf(), ChatMessage::id)
    val orderedActivities = activities.sortedWith(compareBy(ToolActivity::createdAt, ToolActivity::id))
    val activitiesByMessage = orderedActivities
        .filter { it.messageId in assistantMessageIds }
        .groupBy { checkNotNull(it.messageId) }

    return ArrangedChatMessages(
        groups = messages.map { message ->
            ChatMessageGroup(message, activitiesByMessage[message.id].orEmpty())
        },
        unboundActivities = orderedActivities.filter { it.messageId !in assistantMessageIds },
    )
}
