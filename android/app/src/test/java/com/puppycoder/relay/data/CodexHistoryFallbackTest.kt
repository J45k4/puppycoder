package com.puppycoder.relay.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexHistoryFallbackTest {
    @Test
    fun fallsBackWhenExperimentalPaginationIsUnavailable() {
        assertTrue("thread/turns/list requires experimentalApi capability".requiresStableCodexHistory())
        assertTrue("Method not found: thread/turns/list".requiresStableCodexHistory())
        assertTrue("Unsupported method".requiresStableCodexHistory())
        assertTrue("thread/turns/list is not supported".requiresStableCodexHistory())
    }

    @Test
    fun preservesOrdinaryErrors() {
        assertFalse("Could not connect to Codex".requiresStableCodexHistory())
        assertFalse("Authentication failed".requiresStableCodexHistory())
        assertFalse("Thread not found".requiresStableCodexHistory())
    }

    @Test
    fun refreshesOpenHistoryForThreadTurnAndItemEvents() {
        assertTrue("turn/started".updatesOpenConversation())
        assertTrue("item/agentMessage/delta".updatesOpenConversation())
        assertTrue("thread/status/changed".updatesOpenConversation())
        assertFalse("account/updated".updatesOpenConversation())
    }
}
