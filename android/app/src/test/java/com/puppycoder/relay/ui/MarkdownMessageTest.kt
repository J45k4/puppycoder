package com.puppycoder.relay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMessageTest {
    @Test
    fun parsesCommonChatMarkdownBlocks() {
        val blocks = parseMarkdownBlocks(
            """
            # Result

            - first
            2. second
            > note

            ```kotlin
            val puppy = true
            ```
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Heading(1, "Result"), blocks[0])
        assertEquals(MarkdownBlock.ListItem("•", "first"), blocks[1])
        assertEquals(MarkdownBlock.ListItem("2.", "second"), blocks[2])
        assertEquals(MarkdownBlock.Quote("note"), blocks[3])
        assertEquals(MarkdownBlock.Code("kotlin", "val puppy = true"), blocks[4])
    }

    @Test
    fun keepsUnclosedFenceAsCode() {
        val blocks = parseMarkdownBlocks("```\nhello")

        assertTrue(blocks.single() is MarkdownBlock.Code)
        assertEquals("hello", (blocks.single() as MarkdownBlock.Code).text)
    }

    @Test
    fun parsesAlignedMarkdownTable() {
        val blocks = parseMarkdownBlocks(
            """
            | Metric | Before | Optimized |
            |:---|---:|:---:|
            | Janky frames | 6.71% | 6.67% median |
            | Escaped | one \| two | `a|b` |
            """.trimIndent(),
        )

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("Metric", "Before", "Optimized"), table.headers)
        assertEquals(
            listOf(MarkdownTableAlignment.START, MarkdownTableAlignment.END, MarkdownTableAlignment.CENTER),
            table.alignments,
        )
        assertEquals(listOf("Janky frames", "6.71%", "6.67% median"), table.rows[0])
        assertEquals(listOf("Escaped", "one | two", "`a|b`"), table.rows[1])
    }
}
