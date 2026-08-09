package com.puppycoder.relay

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuppyCoderSearchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchRemainsAttachedWhileChatListScrolls() {
        compose.onNodeWithContentDescription("Search chats").performClick()
        compose.waitForIdle()
        repeat(3) {
            compose.onRoot().performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close search").fetchSemanticsNode()
    }
}
