package com.metrolist.music.arc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The parts of GitHub markdown our release notes use: headings, nested "-" lists, **bold**, bare links and @names. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Bullet(val depth: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock
}

fun parseMarkdown(text: String): List<MdBlock> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("#") -> MdBlock.Heading(trimmed.takeWhile { it == '#' }.length, trimmed.trimStart('#').trim())
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                MdBlock.Bullet(line.takeWhile { it == ' ' }.length / 2, trimmed.substring(2).trim())
            else -> MdBlock.Paragraph(trimmed)
        }
    }

private val linkRegex = Regex("(@[a-zA-Z0-9_-]+)|(https?://[\\w-]+(\\.[\\w-]+)+[\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")

fun markdownInline(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        val parts = text.split("**")
        // A last unpaired "**" stays as written.
        val unpaired = parts.size % 2 == 0
        parts.forEachIndexed { index, part ->
            val dangling = unpaired && index == parts.lastIndex
            if (dangling) append("**")
            val bold = index % 2 == 1 && !dangling
            if (bold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            var last = 0
            linkRegex.findAll(part).forEach { match ->
                append(part.substring(last, match.range.first))
                val url = if (match.value.startsWith("@")) "https://github.com/${match.value.drop(1)}" else match.value
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor)))) { append(match.value) }
                last = match.range.last + 1
            }
            append(part.substring(last))
            if (bold) pop()
        }
    }

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val blocks = remember(text) { parseMarkdown(text) }
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val body = style.copy(color = colors.onSurface)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading ->
                    Text(
                        markdownInline(block.text, colors.primary),
                        style =
                            when (block.level) {
                                1 -> type.headlineSmall
                                2 -> type.titleLarge
                                else -> type.titleMedium
                            }.copy(fontWeight = FontWeight.Bold, color = colors.onSurface),
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 2.dp),
                    )
                is MdBlock.Bullet ->
                    Row(Modifier.padding(start = (block.depth * 16).dp)) {
                        Text(if (block.depth == 0) "•" else "◦", style = body, modifier = Modifier.width(16.dp))
                        Text(markdownInline(block.text, colors.primary), style = body)
                    }
                is MdBlock.Paragraph -> Text(markdownInline(block.text, colors.primary), style = body, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
