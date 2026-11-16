package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.SmallGridThumbnailHeight
import androidx.compose.foundation.layout.Column
import com.metrolist.music.utils.rememberEnumPreference

@Composable
fun KeepListeningModule(env: HomeEnv) {
    val keepListening by env.viewModel.keepListening.collectAsStateWithLifecycle()
    val items = keepListening?.takeIf { it.isNotEmpty() } ?: return
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val textHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 + MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
        }
    val rows = if (items.size > 6) 2 else 1

    Column {
        ModuleHeader(stringResource(R.string.keep_listening))
        LazyHorizontalGrid(
            state = rememberLazyGridState(),
            rows = GridCells.Fixed(rows),
            contentPadding = HomeRowPadding,
            modifier = Modifier.fillMaxWidth().height((gridHeight + textHeight) * rows),
        ) {
            items(items.distinctBy { it.id }, key = { it.id }) { HomeLocalGridItem(env, it) }
        }
    }
}
