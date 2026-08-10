package com.puppycoder.relay.ui

import com.puppycoder.relay.data.ChatMessage
import com.puppycoder.relay.data.DeliveryState
import com.puppycoder.relay.data.MessageRole
import com.puppycoder.relay.data.ToolActivity
import com.puppycoder.relay.data.ToolActivityState
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageGroupingTest {
    @Test
    fun groupsThinkingAndToolsWithAssistantWhileKeepingResponseBody() {
        val user = message("user", MessageRole.USER, "Please inspect it")
        val assistant = message("assistant", MessageRole.ASSISTANT, "The final answer stays visible.")
        val tools = listOf(
            activity("tool", "assistant", "Shell", 20),
            activity("thought", "assistant", "Thinking", 10),
        )

        val arranged = arrangeMessageActivities(listOf(user, assistant), tools)

        assertEquals("The final answer stays visible.", arranged.groups[1].message.body)
        assertEquals(listOf("Thinking", "Shell"), arranged.groups[1].activities.map(ToolActivity::title))
        assertEquals(emptyList<ToolActivity>(), arranged.unboundActivities)
    }

    @Test
    fun keepsActivityVisibleBeforeAssistantMessageStartsStreaming() {
        val activity = activity("tool", "future-assistant", "Read file", 10)

        val arranged = arrangeMessageActivities(
            messages = listOf(message("user", MessageRole.USER, "Inspect it")),
            activities = listOf(activity),
        )

        assertEquals(listOf(activity), arranged.unboundActivities)
    }

    private fun message(id: String, role: MessageRole, body: String) = ChatMessage(
        id = id,
        conversationId = "chat",
        role = role,
        body = body,
        deliveryState = DeliveryState.DELIVERED,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun activity(id: String, messageId: String, title: String, createdAt: Long) = ToolActivity(
        id = id,
        conversationId = "chat",
        messageId = messageId,
        title = title,
        detail = "detail",
        state = ToolActivityState.COMPLETED,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
