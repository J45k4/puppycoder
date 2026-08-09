package com.puppycoder.relay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.puppycoder.relay.ui.edgeSwipeBack
import org.junit.Rule
import org.junit.Test

class SwipeBackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rightSwipeFromLeftEdgeNavigatesBack() {
        compose.setContent {
            var showingChat by remember { mutableStateOf(true) }
            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .edgeSwipeBack { showingChat = false },
                ) {
                    Text(if (showingChat) "Chat" else "Chats")
                }
            }
        }

        compose.onRoot().performTouchInput {
            swipeRight(startX = 1f, endX = 500f, durationMillis = 300)
        }
        compose.onNodeWithText("Chats").fetchSemanticsNode()
    }
}
