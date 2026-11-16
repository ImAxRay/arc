package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import androidx.compose.foundation.layout.Column
import com.metrolist.music.ui.screens.MoodAndGenresButton
import com.metrolist.music.ui.screens.MoodAndGenresButtonHeight

@Composable
internal fun moodsAvailable(env: HomeEnv): Boolean {
    val explorePage by env.viewModel.explorePage.collectAsStateWithLifecycle()
    return explorePage?.moodAndGenres != null
}

@Composable
fun MoodsModule(env: HomeEnv) {
    val explorePage by env.viewModel.explorePage.collectAsStateWithLifecycle()
    val moods = explorePage?.moodAndGenres ?: return
    Column {
        ModuleHeader(stringResource(R.string.mood_and_genres), onClick = { env.navController.navigate("mood_and_genres") })
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
        ) {
            val key = { it: com.metrolist.innertube.pages.MoodAndGenres.Item -> "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}" }
            items(moods.distinctBy(key), key = key) {
                MoodAndGenresButton(
                    title = it.title,
                    onClick = { env.navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}") },
                    modifier = Modifier.padding(6.dp).width(180.dp),
                )
            }
        }
    }
}
