package com.metrolist.music.arc

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTest {
    private val notes =
        """
        ## 0.9.3

        Everything since 0.9.0.

        ### Home

        - **Home is built from modules now**, stacked in one list.
        - **Music videos (Beta).**
          - A full-screen watch view, see https://github.com/ImAxRay/arc by @ImAxRay
        """.trimIndent()

    @Test
    fun blocks() {
        assertEquals(
            listOf(
                MdBlock.Heading(2, "0.9.3"),
                MdBlock.Paragraph("Everything since 0.9.0."),
                MdBlock.Heading(3, "Home"),
                MdBlock.Bullet(0, "**Home is built from modules now**, stacked in one list."),
                MdBlock.Bullet(0, "**Music videos (Beta).**"),
                MdBlock.Bullet(1, "A full-screen watch view, see https://github.com/ImAxRay/arc by @ImAxRay"),
            ),
            parseMarkdown(notes),
        )
    }

    @Test
    fun inlineLeavesNoMarkers() {
        parseMarkdown(notes).forEach { block ->
            val text = when (block) {
                is MdBlock.Heading -> block.text
                is MdBlock.Bullet -> block.text
                is MdBlock.Paragraph -> block.text
            }
            val shown = markdownInline(text, Color.Blue)
            assertFalse(shown.text, shown.text.contains("**") || shown.text.startsWith("#"))
        }
        val styled = markdownInline("**bold** plain", Color.Blue)
        assertEquals("bold plain", styled.text)
        assertEquals(0 until 4, styled.spanStyles.single().let { it.start until it.end })
        assertEquals("a **b", markdownInline("a **b", Color.Blue).text)
        assertEquals(2, markdownInline("see https://x.io and @me", Color.Blue).getLinkAnnotations(0, 24).size)
    }
}
