package com.metrolist.music.lyrics

import com.metrolist.music.lyrics.LyricsUtils.romanize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RomanizationDetectionTest {

    private val all = listOf(
        "Japanese", "Korean", "Chinese", "Hindi", "Punjabi", "Russian", "Ukrainian",
        "Serbian", "Bulgarian", "Belarusian", "Kyrgyz", "Macedonian",
    )

    private fun lrc(vararg lines: String) =
        lines.mapIndexed { i, l -> "[00:%02d.00]%s".format(i * 3, l) }.joinToString("\n")

    private fun roman(song: String, line: String, byLine: Boolean = false) =
        runBlocking { romanize(song, line, all, byLine) }

    @Test
    fun `japanese with kanji is read as japanese, not chinese`() {
        val song = lrc("夜に駆ける", "沈むように溶けてゆくように", "二人だけの空が広がる夜に")
        val r = roman(song, "夜に駆ける")
        assertNotNull(r)
        assertTrue("got $r", r!!.contains("ni"))
        val kanjiOnly = roman(song, "夜空")
        assertNotNull(kanjiOnly)
        assertTrue("got $kanjiOnly", !kanjiOnly!!.contains("ye"))
    }

    @Test
    fun `korean lines romanize and english lines in the same song stay alone`() {
        val song = lrc("Cause I know what you like boy", "너는 내 hype boy", "내가 니 hype boy")
        assertEquals("neoneun nae hype boy", roman(song, "너는 내 hype boy"))
        assertNull(roman(song, "Cause I know what you like boy"))
    }

    @Test
    fun `russian reads as russian even on lines without its own letters`() {
        val song = lrc("Я хочу быть с тобой", "Твоё имя давно стало другим", "Hello")
        val r = roman(song, "Твоё имя давно стало другим")
        assertNotNull(r)
        assertTrue("got $r", r!!.lowercase().startsWith("tvoyo"))
        val shared = roman(song, "Я хочу", byLine = true)
        assertNotNull(shared)
        assertNull(roman(song, "Hello"))
    }

    @Test
    fun `chinese lyrics give pinyin`() {
        val song = lrc("我爱你", "你好吗")
        assertEquals("wo ai ni", roman(song, "我爱你"))
    }

    @Test
    fun `a disabled language is left alone`() = runBlocking {
        val song = lrc("너는 내 hype boy")
        assertNull(romanize(song, "너는 내 hype boy", listOf("Japanese"), false))
    }
}
