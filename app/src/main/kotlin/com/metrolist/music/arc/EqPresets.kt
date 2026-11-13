package com.metrolist.music.arc

import com.metrolist.music.eq.data.FilterType
import com.metrolist.music.eq.data.ParametricEQBand
import com.metrolist.music.eq.data.SavedEQProfile

private val ArcPresetOrder = listOf("bass_boost", "bass_cut", "vocal", "treble", "loudness", "warm")

/** Starter presets, saved like imported ones so they can be edited or deleted. Preamps leave headroom for the boosts. */
internal val ArcEqPresets: List<SavedEQProfile> =
    listOf(
        preset("bass_boost", "Bass boost", -6.0, ParametricEQBand(105.0, 6.0, 0.7, FilterType.LSC)),
        preset("bass_cut", "Bass cut", 0.0, ParametricEQBand(120.0, -6.0, 0.7, FilterType.LSC)),
        preset(
            "vocal",
            "Vocal",
            -4.0,
            ParametricEQBand(250.0, -2.0, 1.0),
            ParametricEQBand(2500.0, 4.0, 1.0),
            ParametricEQBand(5000.0, 2.0, 1.4),
        ),
        preset("treble", "Treble", -5.0, ParametricEQBand(6000.0, 5.0, 0.7, FilterType.HSC)),
        preset(
            "loudness",
            "Loudness",
            -5.0,
            ParametricEQBand(90.0, 5.0, 0.7, FilterType.LSC),
            ParametricEQBand(9000.0, 4.0, 0.7, FilterType.HSC),
        ),
        preset(
            "warm",
            "Warm",
            -3.0,
            ParametricEQBand(200.0, 3.0, 0.7, FilterType.LSC),
            ParametricEQBand(8000.0, -3.0, 0.7, FilterType.HSC),
        ),
    )

private fun preset(id: String, name: String, preamp: Double, vararg bands: ParametricEQBand) =
    SavedEQProfile(
        id = "arc_$id",
        name = name,
        deviceModel = name,
        bands = bands.toList(),
        preamp = preamp,
        source = "Arc",
        isCustom = true,
        // Fixed and ascending so they list in this order after the user's own, which are newer.
        addedTimestamp = 1_000L - ArcPresetOrder.indexOf(id),
    )
