package com.puppycoder.relay.ui

import com.puppycoder.relay.data.ChatListItem
import com.puppycoder.relay.data.Conversation
import com.puppycoder.relay.data.RelayServer
import com.puppycoder.relay.data.ServerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatArrangementTest {
    private val codex = RelayServer(
        id = "codex",
        name = "Workstation",
        kind = ServerKind.CODEX,
        endpoint = "http://codex",
        workspace = "/work",
    )
    private val openCode = RelayServer(
        id = "opencode",
        name = "Laptop",
        kind = ServerKind.OPENCODE,
        endpoint = "http://opencode",
        workspace = "/code",
    )

    @Test
    fun sortsChatsBySelectedOrder() {
        val chats = listOf(chat("z", "Zebra", 100, codex), chat("a", "Alpha", 200, openCode))

        assertEquals(
            listOf("Alpha", "Zebra"),
            arrangeChats(chats, ChatSortOrder.RECENT, ChatGroupMode.NONE).single().chats.map { it.conversation.title },
        )
        assertEquals(
            listOf("Zebra", "Alpha"),
            arrangeChats(chats, ChatSortOrder.OLDEST, ChatGroupMode.NONE).single().chats.map { it.conversation.title },
        )
        assertEquals(
            listOf("Alpha", "Zebra"),
            arrangeChats(chats, ChatSortOrder.TITLE, ChatGroupMode.NONE).single().chats.map { it.conversation.title },
        )
    }

    @Test
    fun groupsChatsByComputerAndAgent() {
        val chats = listOf(chat("one", "One", 100, codex), chat("two", "Two", 200, openCode))

        assertEquals(
            listOf("Laptop", "Workstation"),
            arrangeChats(chats, ChatSortOrder.RECENT, ChatGroupMode.COMPUTER).map { it.title },
        )
        assertEquals(
            listOf("Codex", "OpenCode"),
            arrangeChats(chats, ChatSortOrder.RECENT, ChatGroupMode.AGENT).map { it.title },
        )
    }

    @Test
    fun groupsChatsByProjectAndShowsWorkspacePath() {
        val chats = listOf(
            chat("one", "One", 100, codex, "/work/puppycoder"),
            chat("two", "Two", 200, openCode, "/work/puppycoder"),
            chat("three", "Three", 300, codex, "C:\\src\\relay"),
        )

        val sections = arrangeChats(chats, ChatSortOrder.RECENT, ChatGroupMode.PROJECT)

        assertEquals(listOf("puppycoder", "relay"), sections.map { it.title })
        assertEquals(listOf("/work/puppycoder", "C:\\src\\relay"), sections.map { it.subtitle })
        assertEquals(2, sections.first().chats.size)
    }

    private fun chat(
        id: String,
        title: String,
        updatedAt: Long,
        computer: RelayServer,
        workspace: String = computer.workspace,
    ) = ChatListItem(
        conversation = Conversation(
            id = id,
            computerId = computer.id,
            title = title,
            workspace = workspace,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        ),
        computer = computer,
    )
}
