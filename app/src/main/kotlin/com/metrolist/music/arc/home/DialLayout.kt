package com.metrolist.music.arc.home

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the user put in a dial cell. Cells without one take the dial's changing items. */
sealed interface DialPanel {
    data object Continue : DialPanel
    data object Dice : DialPanel
    data object Radio : DialPanel
    /** The guided playlist. */
    data object Plus : DialPanel
    /** Opens Moods & genres. */
    data object Moods : DialPanel
    /** One mood's mixes, by the chip's title and params (titles are localised, params may change; either finds it). */
    data class Mood(val title: String, val params: String?) : DialPanel
    /** An item pinned to this cell; it lives in the speed_dial_item table like every pin. */
    data class Item(val id: String) : DialPanel
}

data class DialCell(val page: Int, val row: Int, val col: Int)

/** Where Continue and the buttons sit until the user moves something: Continue top left, the rest in the far corner, Sleep and radio on later pages. */
internal fun defaultDialPanels(columns: Int, rows: Int): Map<DialCell, DialPanel> =
    buildMap {
        put(DialCell(0, 0, 0), DialPanel.Continue)
        put(DialCell(0, rows - 1, columns - 1), DialPanel.Dice)
        if (columns >= 2) put(DialCell(0, rows - 1, columns - 2), DialPanel.Moods)
        if (rows >= 2) put(DialCell(0, rows - 2, columns - 1), DialPanel.Plus)
        // big Sleep on page two, big discovery radio on page three
        if (rows >= 3 && columns >= 3) {
            put(DialCell(1, 1, columns - 2), DialPanel.Mood("Sleep", null))
            put(DialCell(2, rows - 2, columns - 2), DialPanel.Radio)
        }
    }

internal fun defaultDialSizes(columns: Int, rows: Int): Map<DialCell, Int> =
    if (rows >= 3 && columns >= 3) mapOf(DialCell(1, 1, columns - 2) to 2, DialCell(2, rows - 2, columns - 2) to 2) else emptyMap()

internal fun encodeDialPanels(panels: Map<DialCell, DialPanel>): String =
    panels.entries.joinToString("\n") { (cell, panel) ->
        val value =
            when (panel) {
                DialPanel.Continue -> "continue"
                DialPanel.Dice -> "dice"
                DialPanel.Radio -> "radio"
                DialPanel.Plus -> "plus"
                DialPanel.Moods -> "moods"
                is DialPanel.Mood -> "mood:${panel.title}\t${panel.params.orEmpty()}"
                is DialPanel.Item -> "item:${panel.id}"
            }
        "${cell.page}.${cell.row}.${cell.col}=$value"
    }

/** Tile sizes in cells across, by the cell a panel sits at; a panel without one takes its default. */
internal fun encodeDialSizes(sizes: Map<DialCell, Int>): String = sizes.entries.joinToString("\n") { (cell, span) -> "${cell.page}.${cell.row}.${cell.col}=$span" }

internal fun parseDialSizes(text: String): Map<DialCell, Int> =
    text.lineSequence().mapNotNull { line ->
        val (place, value) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val (page, row, col) = place.split(".").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 } ?: return@mapNotNull null
        DialCell(page, row, col) to (value.toIntOrNull()?.takeIf { it in 1..3 } ?: return@mapNotNull null)
    }.toMap()

/** Lines it can't read are dropped, so an older or newer Arc never breaks the dial. */
internal fun parseDialPanels(text: String): Map<DialCell, DialPanel> =
    text.lineSequence().mapNotNull { line ->
        val (place, value) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val (page, row, col) = place.split(".").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 } ?: return@mapNotNull null
        val panel =
            when {
                value == "continue" -> DialPanel.Continue
                value == "dice" -> DialPanel.Dice
                value == "radio" -> DialPanel.Radio
                value == "plus" -> DialPanel.Plus
                value == "moods" -> DialPanel.Moods
                value.startsWith("mood:") -> value.removePrefix("mood:").split("\t", limit = 2).let { DialPanel.Mood(it[0], it.getOrNull(1)?.ifEmpty { null }) }
                value.startsWith("item:") -> DialPanel.Item(value.removePrefix("item:"))
                else -> return@mapNotNull null
            }
        DialCell(page, row, col) to panel
    }.toMap()

/**
 * The dial's layout, kept in the `arc_dial` prefs file (backed up with the other Arc files): the panels the user placed
 * (absent until the first edit, so the default follows the grid size), the items they took off the dial, and the dice.
 */
class DialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _panels = MutableStateFlow(prefs.getString(PANELS, null)?.let(::parseDialPanels))
    private val _sizes = MutableStateFlow(prefs.getString(SIZES, null)?.let(::parseDialSizes).orEmpty())
    private val _hidden = MutableStateFlow(prefs.getStringSet(HIDDEN, null).orEmpty().toSet())
    private val _dice = MutableStateFlow(prefs.getInt(DICE, 0))

    /** Null until the user edits the dial. */
    val panels: StateFlow<Map<DialCell, DialPanel>?> = _panels
    val sizes: StateFlow<Map<DialCell, Int>> = _sizes
    val hidden: StateFlow<Set<String>> = _hidden
    /** The die's last face, 1 to 6; 0 before the first roll. */
    val dice: StateFlow<Int> = _dice

    /** Items pinned to cells, in the dial's reading order, for Android Auto's grid. */
    fun pinnedOrder(): List<String> =
        _panels.value.orEmpty().entries
            .sortedWith(compareBy({ it.key.page }, { it.key.row }, { it.key.col }))
            .mapNotNull { (it.value as? DialPanel.Item)?.id }

    fun setPanels(panels: Map<DialCell, DialPanel>, sizes: Map<DialCell, Int> = _sizes.value) {
        val kept = sizes.filterKeys { it in panels }
        _panels.value = panels
        _sizes.value = kept
        prefs.edit {
            putString(PANELS, encodeDialPanels(panels))
            putString(SIZES, encodeDialSizes(kept))
        }
    }

    /** Puts back a layout saved earlier; null panels means the default one. */
    fun restore(panels: Map<DialCell, DialPanel>?, sizes: Map<DialCell, Int>, hidden: Set<String>) {
        _panels.value = panels
        _sizes.value = sizes
        _hidden.value = hidden
        prefs.edit {
            if (panels == null) remove(PANELS) else putString(PANELS, encodeDialPanels(panels))
            putString(SIZES, encodeDialSizes(sizes))
            putStringSet(HIDDEN, hidden)
        }
    }

    fun hide(id: String) {
        _hidden.value += id
        prefs.edit { putStringSet(HIDDEN, _hidden.value) }
    }

    fun setDice(face: Int) {
        _dice.value = face
        prefs.edit { putInt(DICE, face) }
    }

    companion object {
        const val FILE = "arc_dial"
        private const val PANELS = "panels"
        private const val SIZES = "sizes"
        private const val HIDDEN = "hidden"
        private const val DICE = "dice"

        @Volatile private var instance: DialStore? = null

        fun get(context: Context): DialStore = instance ?: synchronized(this) { instance ?: DialStore(context).also { instance = it } }
    }
}
