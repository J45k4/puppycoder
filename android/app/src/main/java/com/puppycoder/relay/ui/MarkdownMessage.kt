package com.puppycoder.relay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<MarkdownTableAlignment>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
}

internal enum class MarkdownTableAlignment { START, CENTER, END }

private val fencedCodeStart = Regex("^\\s*```(.*)$")
private val fencedCodeEnd = Regex("^\\s*```\\s*$")
private val headingStart = Regex("^(#{1,6})\\s+(.+)$")
private val quoteStart = Regex("^\\s*>\\s?(.*)$")
private val unorderedItemStart = Regex("^\\s*[-+*]\\s+(.+)$")
private val orderedItemStart = Regex("^\\s*(\\d+)\\.\\s+(.+)$")
private val anyFencedCodeStart = Regex("^\\s*```")
private val anyHeadingStart = Regex("^#{1,6}\\s+")
private val anyQuoteStart = Regex("^\\s*>\\s?")
private val anyUnorderedItemStart = Regex("^\\s*[-+*]\\s+")
private val anyOrderedItemStart = Regex("^\\s*\\d+\\.\\s+")

@Composable
internal fun MarkdownMessage(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownText(block.text, linkColor)
                is MarkdownBlock.Heading -> MarkdownText(
                    text = block.text,
                    linkColor = linkColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = when (block.level) {
                        1 -> 22
                        2 -> 20
                        3 -> 18
                        else -> 16
                    },
                )
                is MarkdownBlock.Quote -> Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .heightIn(min = 24.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(9.dp))
                    MarkdownText(
                        text = block.text,
                        linkColor = linkColor,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is MarkdownBlock.ListItem -> Row(verticalAlignment = Alignment.Top) {
                    Text(block.marker, modifier = Modifier.width(25.dp), fontWeight = FontWeight.SemiBold)
                    MarkdownText(block.text, linkColor, Modifier.weight(1f))
                }
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Table -> MarkdownTable(block, linkColor)
            }
        }
    }
}

@Composable
private fun MarkdownText(
    text: String,
    linkColor: Color,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: Int = 15,
    textAlign: TextAlign? = null,
) {
    val formatted = remember(text, linkColor) { inlineMarkdown(text, linkColor) }
    SelectionContainer(modifier = modifier) {
        Text(
            text = formatted,
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 6).sp,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table, linkColor: Color) {
    val columnWidths = remember(block) {
        block.headers.indices.map { column ->
            val longest = sequenceOf(block.headers[column])
                .plus(block.rows.asSequence().map { it.getOrElse(column) { "" } })
                .maxOf(String::length)
            (longest * 7 + 24).coerceIn(96, 220).dp
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .45f),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            MarkdownTableRow(block.headers, block.alignments, columnWidths, linkColor, header = true)
            HorizontalDivider()
            block.rows.forEachIndexed { index, row ->
                MarkdownTableRow(row, block.alignments, columnWidths, linkColor, header = false)
                if (index != block.rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    alignments: List<MarkdownTableAlignment>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    linkColor: Color,
    header: Boolean,
) {
    Row {
        columnWidths.indices.forEach { column ->
            if (column > 0) {
                Box(Modifier.width(1.dp).heightIn(min = 38.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            MarkdownText(
                text = cells.getOrElse(column) { "" },
                linkColor = linkColor,
                modifier = Modifier.width(columnWidths[column]).padding(horizontal = 10.dp, vertical = 8.dp),
                fontWeight = if (header) FontWeight.SemiBold else null,
                fontSize = 13,
                textAlign = when (alignments.getOrElse(column) { MarkdownTableAlignment.START }) {
                    MarkdownTableAlignment.START -> TextAlign.Start
                    MarkdownTableAlignment.CENTER -> TextAlign.Center
                    MarkdownTableAlignment.END -> TextAlign.End
                },
            )
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
    ) {
        Column {
            block.language?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            SelectionContainer {
                Text(
                    text = block.text,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

internal fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    if (source.isEmpty()) return listOf(MarkdownBlock.Paragraph(""))
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }

        val fence = fencedCodeStart.matchEntire(line)
        if (fence != null) {
            val language = fence.groupValues[1].trim().ifEmpty { null }
            val code = mutableListOf<String>()
            index++
            while (index < lines.size && !fencedCodeEnd.matches(lines[index])) {
                code += lines[index++]
            }
            if (index < lines.size) index++
            blocks += MarkdownBlock.Code(language, code.joinToString("\n"))
            continue
        }

        val parsedTable = parseMarkdownTable(lines, index)
        if (parsedTable != null) {
            val (table, nextIndex) = parsedTable
            blocks += table
            index = nextIndex
            continue
        }

        val heading = headingStart.matchEntire(line)
        if (heading != null) {
            blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            index++
            continue
        }
        val quote = quoteStart.matchEntire(line)
        if (quote != null) {
            blocks += MarkdownBlock.Quote(quote.groupValues[1])
            index++
            continue
        }
        val unorderedItem = unorderedItemStart.matchEntire(line)
        if (unorderedItem != null) {
            blocks += MarkdownBlock.ListItem("•", unorderedItem.groupValues[1])
            index++
            continue
        }
        val orderedItem = orderedItemStart.matchEntire(line)
        if (orderedItem != null) {
            blocks += MarkdownBlock.ListItem("${orderedItem.groupValues[1]}.", orderedItem.groupValues[2])
            index++
            continue
        }

        val paragraph = mutableListOf(line)
        index++
        while (index < lines.size && lines[index].isNotBlank() && !startsMarkdownBlock(lines[index])) {
            paragraph += lines[index++]
        }
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
    }
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph("")) }
}

private fun parseMarkdownTable(lines: List<String>, start: Int): Pair<MarkdownBlock.Table, Int>? {
    if (start + 1 >= lines.size || '|' !in lines[start]) return null
    val headers = splitMarkdownTableRow(lines[start])
    val separators = splitMarkdownTableRow(lines[start + 1])
    if (headers.size < 2 || separators.size != headers.size || separators.any { !isTableSeparator(it) }) return null
    val alignments = separators.map { separator ->
        val trimmed = separator.trim()
        when {
            trimmed.startsWith(':') && trimmed.endsWith(':') -> MarkdownTableAlignment.CENTER
            trimmed.endsWith(':') -> MarkdownTableAlignment.END
            else -> MarkdownTableAlignment.START
        }
    }
    val rows = mutableListOf<List<String>>()
    var index = start + 2
    while (index < lines.size && lines[index].isNotBlank() && '|' in lines[index]) {
        val cells = splitMarkdownTableRow(lines[index])
        rows += List(headers.size) { column -> cells.getOrElse(column) { "" } }
        index++
    }
    return MarkdownBlock.Table(headers, alignments, rows) to index
}

private fun isTableSeparator(cell: String): Boolean {
    val trimmed = cell.trim()
    val core = trimmed.removePrefix(":").removeSuffix(":")
    return core.length >= 3 && core.all { it == '-' }
}

private fun splitMarkdownTableRow(line: String): List<String> {
    val source = line.trim().removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var escaped = false
    var inCode = false
    source.forEach { character ->
        when {
            escaped -> {
                cell.append(character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == '`' -> {
                inCode = !inCode
                cell.append(character)
            }
            character == '|' && !inCode -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(character)
        }
    }
    if (escaped) cell.append('\\')
    cells += cell.toString().trim()
    return cells
}

private fun startsMarkdownBlock(line: String): Boolean =
    anyFencedCodeStart.containsMatchIn(line) ||
        anyHeadingStart.containsMatchIn(line) ||
        anyQuoteStart.containsMatchIn(line) ||
        anyUnorderedItemStart.containsMatchIn(line) ||
        anyOrderedItemStart.containsMatchIn(line)

private fun inlineMarkdown(source: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    fun appendRange(value: String) {
        var index = 0
        while (index < value.length) {
            when {
                value[index] == '\\' && index + 1 < value.length -> {
                    append(value[index + 1])
                    index += 2
                }
                value.startsWith("**", index) -> {
                    val end = value.indexOf("**", index + 2)
                    if (end > index + 2) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendRange(value.substring(index + 2, end))
                        pop()
                        index = end + 2
                    } else append(value[index++])
                }
                value.startsWith("~~", index) -> {
                    val end = value.indexOf("~~", index + 2)
                    if (end > index + 2) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        appendRange(value.substring(index + 2, end))
                        pop()
                        index = end + 2
                    } else append(value[index++])
                }
                value[index] == '`' -> {
                    val end = value.indexOf('`', index + 1)
                    if (end > index + 1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.Gray.copy(alpha = .18f),
                            ),
                        )
                        append(value.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else append(value[index++])
                }
                value[index] == '[' -> {
                    val labelEnd = value.indexOf(']', index + 1)
                    val urlEnd = if (labelEnd >= 0 && value.getOrNull(labelEnd + 1) == '(') {
                        value.indexOf(')', labelEnd + 2)
                    } else -1
                    if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                        val url = value.substring(labelEnd + 2, urlEnd)
                        pushLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        )
                        append(value.substring(index + 1, labelEnd))
                        pop()
                        index = urlEnd + 1
                    } else append(value[index++])
                }
                value[index] == '*' || value[index] == '_' -> {
                    val marker = value[index]
                    val end = value.indexOf(marker, index + 1)
                    if (end > index + 1) {
                        pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        appendRange(value.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else append(value[index++])
                }
                else -> append(value[index++])
            }
        }
    }
    appendRange(source)
}
