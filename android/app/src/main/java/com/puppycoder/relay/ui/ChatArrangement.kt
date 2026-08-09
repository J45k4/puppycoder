package com.puppycoder.relay.ui

import com.puppycoder.relay.data.ChatListItem

enum class ChatSortOrder(val label: String) {
    RECENT("Most recent"),
    OLDEST("Oldest first"),
    TITLE("Title A–Z"),
}

enum class ChatGroupMode(val label: String) {
    NONE("No grouping"),
    PROJECT("Project"),
    COMPUTER("Computer"),
    AGENT("Agent"),
}

internal data class ChatSection(
    val key: String,
    val title: String?,
    val subtitle: String? = null,
    val chats: List<ChatListItem>,
)

internal fun arrangeChats(
    chats: List<ChatListItem>,
    sortOrder: ChatSortOrder,
    groupMode: ChatGroupMode,
): List<ChatSection> {
    val sorted = when (sortOrder) {
        ChatSortOrder.RECENT -> chats.sortedByDescending { it.conversation.updatedAt }
        ChatSortOrder.OLDEST -> chats.sortedBy { it.conversation.updatedAt }
        ChatSortOrder.TITLE -> chats.sortedWith(
            compareBy<ChatListItem> { it.conversation.title.lowercase() }
                .thenByDescending { it.conversation.updatedAt },
        )
    }

    return when (groupMode) {
        ChatGroupMode.NONE -> listOf(ChatSection("all", null, chats = sorted))
        ChatGroupMode.PROJECT -> sorted
            .groupBy { it.conversation.workspace.trim().ifEmpty { "Unknown project" } }
            .map { (workspace, items) ->
                ChatSection(
                    key = "project:$workspace",
                    title = workspace.projectName(),
                    subtitle = workspace.takeUnless { it == "Unknown project" || it == workspace.projectName() },
                    chats = items,
                )
            }
            .sortedWith(compareBy<ChatSection> { it.title?.lowercase() }.thenBy { it.subtitle?.lowercase() })
        ChatGroupMode.COMPUTER -> sorted
            .groupBy { it.computer.id }
            .map { (computerId, items) ->
                ChatSection(
                    key = "computer:$computerId",
                    title = items.first().computer.name,
                    chats = items,
                )
            }
            .sortedBy { it.title?.lowercase() }
        ChatGroupMode.AGENT -> sorted
            .groupBy { it.computer.kind }
            .map { (kind, items) ->
                ChatSection(key = "agent:${kind.name}", title = kind.label, chats = items)
            }
            .sortedBy { it.title?.lowercase() }
    }
}

private fun String.projectName(): String {
    if (this == "Unknown project") return this
    val normalized = trimEnd('/', '\\')
    return normalized.substringAfterLast('/').substringAfterLast('\\').ifEmpty { this }
}
