package com.puppycoder.relay

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuppyCoderArrangementTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sortAndGroupButtonOpensAllChoices() {
        compose.onNodeWithContentDescription("Sort and group chats").performClick()

        compose.onNodeWithText("Sort chats").fetchSemanticsNode()
        compose.onNodeWithText("Most recent").fetchSemanticsNode()
        compose.onNodeWithText("Oldest first").fetchSemanticsNode()
        compose.onNodeWithText("Title A–Z").fetchSemanticsNode()
        compose.onNodeWithText("Group chats").fetchSemanticsNode()
        compose.onNodeWithText("No grouping").fetchSemanticsNode()
        compose.onNodeWithText("Project").fetchSemanticsNode()
        compose.onNodeWithText("Computer").fetchSemanticsNode()
        compose.onNodeWithText("Agent").fetchSemanticsNode()
    }
}
