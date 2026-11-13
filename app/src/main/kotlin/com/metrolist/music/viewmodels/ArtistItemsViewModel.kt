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
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.utils.dataStore
import com.metrolist.music.arc.ArtistSongSort
import com.metrolist.music.arc.artistAlbumYears
import com.metrolist.music.arc.sortSongs
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_SORT_PAGES = 20

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    private val artistId = savedStateHandle.get<String>("artistId")

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)
    val songSort = MutableStateFlow(ArtistSongSort.POPULAR)
    val sortReversed = MutableStateFlow(false)
    private val albumYears = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sortedItems =
        combine(itemsPage, songSort, albumYears, sortReversed) { page, sort, years, reversed ->
            sortSongs(page?.items.orEmpty(), sort, years).let { if (reversed) it.asReversed() else it }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Anything but YouTube's own order needs every song, so this loads the rest of the list too. */
    fun sortSongsBy(sort: ArtistSongSort) {
        // Choosing the current sort again flips it.
        sortReversed.value = sort == songSort.value && !sortReversed.value
        songSort.value = sort
        if (sort == ArtistSongSort.POPULAR && !sortReversed.value) return
        viewModelScope.launch {
            var pages = 0
            while (itemsPage.value?.continuation != null && pages < MAX_SORT_PAGES) {
                fetchMore()
                pages++
            }
        }
        if (sort == ArtistSongSort.NEWEST && albumYears.value.isEmpty() && artistId != null) {
            viewModelScope.launch { albumYears.value = artistAlbumYears(artistId) }
        }
    }

    init {
        viewModelScope.launch {
            YouTube
                .artistItems(
                    BrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                )                .onSuccess { artistItemsPage ->
                    val resolvedItems = YouTube.resolveArtistIds(artistItemsPage.items)
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    title.value = artistItemsPage.title
                    itemsPage.value =
                        ItemsPage(
                            items = resolvedItems
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsPage.continuation,
                        )
                }.onFailure {
                    reportException(it)
                }
        }
    }

    fun loadMore() {
        viewModelScope.launch { fetchMore() }
    }

    private suspend fun fetchMore() {
        run {
            val oldItemsPage = itemsPage.value ?: return
            val continuation = oldItemsPage.continuation ?: return
            YouTube
                .artistItemsContinuation(continuation)
                .onSuccess { artistItemsContinuationPage ->
                    val resolvedItems = YouTube.resolveArtistIds(artistItemsContinuationPage.items)
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    itemsPage.update {
                        ItemsPage(
                            items =
                            (oldItemsPage.items + resolvedItems)
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsContinuationPage.continuation,
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
