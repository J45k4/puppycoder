package com.puppycoder.relay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.puppycoder.relay.ui.ChatGroupHeader
import org.junit.Rule
import org.junit.Test

class ChatGroupHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun groupHeaderTogglesBetweenExpandedAndCollapsed() {
        compose.setContent {
            var expanded by remember { mutableStateOf(true) }
            MaterialTheme {
                ChatGroupHeader(
                    title = "Workstation",
                    count = 3,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
            }
        }

        compose.onNodeWithContentDescription("Collapse Workstation group").performClick()
        compose.onNodeWithContentDescription("Expand Workstation group").fetchSemanticsNode()
    }
}
