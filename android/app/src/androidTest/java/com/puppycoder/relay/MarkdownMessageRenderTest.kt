package com.puppycoder.relay

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.puppycoder.relay.ui.MarkdownMessage
import org.junit.Rule
import org.junit.Test

class MarkdownMessageRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersStructuredMarkdownBlocks() {
        compose.setContent {
            MaterialTheme {
                MarkdownMessage(
                    """
                    # Heading

                    - **important** item

                    ```kotlin
                    val puppy = true
                    ```
                    """.trimIndent(),
                )
            }
        }

        compose.onNodeWithText("Heading").fetchSemanticsNode()
        compose.onNodeWithText("important item").fetchSemanticsNode()
        compose.onNodeWithText("kotlin").fetchSemanticsNode()
        compose.onNodeWithText("val puppy = true").fetchSemanticsNode()
    }
}
