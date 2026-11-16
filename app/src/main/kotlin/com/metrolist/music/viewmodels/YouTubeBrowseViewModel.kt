/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")
    private val title = savedStateHandle.get<String>("title")

    val result = MutableStateFlow<BrowseResult?>(null)

    init {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            (if (browseId == "FEmusic_home") moodPage() else YouTube.browse(browseId, params))
                .onSuccess {
                    result.value = it
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                }.onFailure {
                    reportException(it)
                }
        }
    }

    /** A mood chip's own home page, every page of it, mixes first, under the chip's name. */
    private suspend fun moodPage(): Result<BrowseResult> =
        YouTube.home(params = params).map { first ->
            val sections = first.sections.toMutableList()
            var continuation = first.continuation
            repeat(MAX_MOOD_PAGES) {
                val next = continuation?.let { YouTube.home(it).getOrNull() } ?: return@repeat
                sections += next.sections
                continuation = next.continuation
            }
            BrowseResult(
                title = title,
                items = sections.sortedByDescending { it.endpoint?.browseId == "FEmusic_mixed_for_you" }.map { BrowseResult.Item(it.title, it.items) },
            )
        }
}

private const val MAX_MOOD_PAGES = 4
