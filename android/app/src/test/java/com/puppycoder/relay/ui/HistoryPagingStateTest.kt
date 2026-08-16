package com.puppycoder.relay.ui

import com.puppycoder.relay.data.ChatMessage
import com.puppycoder.relay.data.DeliveryState
import com.puppycoder.relay.data.HistorySyncPage
import com.puppycoder.relay.data.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPagingStateTest {
    @Test
    fun cachedRemoteMessagesStayVisibleDuringAndAfterInitialRefresh() {
        val cached = ChatMessage(
            conversationId = "chat",
            role = MessageRole.ASSISTANT,
            body = "Cached response",
            deliveryState = DeliveryState.DELIVERED,
            remoteMessageId = "remote-1",
            createdAt = 1_000,
        )
        val loading = HistoryPagingState(
            conversationId = "chat",
            initialLoading = true,
            openedAt = 10_000,
        )

        assertEquals(listOf(cached), visibleMessagesForHistory(listOf(cached), loading))

        val refreshed = loading.withPage(
            page = HistorySyncPage(importedCount = 2, oldestMessageAt = 5_000, nextCursor = "older-1"),
            initial = true,
            cachedOldestAt = cached.createdAt,
        )
        assertEquals(listOf(cached), visibleMessagesForHistory(listOf(cached), refreshed))
    }

    @Test
    fun retainsCachedRangeWhenLatestPageFinishesLoading() {
        val loaded = HistoryPagingState(conversationId = "chat", initialLoading = true).withPage(
            page = HistorySyncPage(importedCount = 2, oldestMessageAt = 5_000, nextCursor = "older-1"),
            initial = true,
            cachedOldestAt = 1_000,
        )

        assertFalse(loaded.initialLoading)
        assertEquals(1_000L, loaded.oldestLoadedAt)
        assertTrue(loaded.hasOlder)
    }

    @Test
    fun followsCursorBackwardsUntilAllHistoryIsLoaded() {
        val latest = HistoryPagingState(conversationId = "chat", initialLoading = true).withPage(
            HistorySyncPage(importedCount = 40, oldestMessageAt = 5_000, nextCursor = "older-1"),
            initial = true,
        )

        assertFalse(latest.initialLoading)
        assertTrue(latest.hasOlder)
        assertEquals(5_000L, latest.oldestLoadedAt)
        assertEquals(0, latest.olderPageVersion)

        val older = latest.withPage(
            HistorySyncPage(importedCount = 40, oldestMessageAt = 2_000, nextCursor = "older-2"),
            initial = false,
        )
        assertEquals(2_000L, older.oldestLoadedAt)
        assertEquals(1, older.olderPageVersion)

        val oldest = older.withPage(
            HistorySyncPage(importedCount = 12, oldestMessageAt = 1_000, nextCursor = null),
            initial = false,
        )
        assertFalse(oldest.hasOlder)
        assertTrue(oldest.allHistoryLoaded)
        assertEquals(2, oldest.olderPageVersion)
    }
}
