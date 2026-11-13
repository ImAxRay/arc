package com.metrolist.music.arc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R

/** A row in the Artists tab that leads to everything taken out of New releases; absent when nothing is. */
@Composable
fun IgnoredLink(navController: NavController) {
    val context = LocalContext.current
    val artists by ReleaseFilter.artists(context).collectAsState()
    val releases by ReleaseFilter.releases(context).collectAsState()
    val count = artists.orEmpty().size + releases.orEmpty().size
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("arc/ignored") }
                .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(R.drawable.person_off), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.arc_ignored_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val artists by ReleaseFilter.artists(context).collectAsState()
    val releases by ReleaseFilter.releases(context).collectAsState()
    val state = rememberLazyListState()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = insets) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(
                        (artists.orEmpty().size + releases.orEmpty().size).toString(),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Thin,
                        lineHeight = 64.sp,
                    )
                    Text(stringResource(R.string.arc_ignored_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.arc_ignored_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (artists.orEmpty().isNotEmpty()) {
                item(key = "artists") { ListSectionHeader(stringResource(R.string.artists)) }
                items(artists.orEmpty(), key = { "artist_${it.id}" }) { entry ->
                    val artist by database.artist(entry.id).collectAsState(initial = null)
                    IgnoredRow(
                        entry = entry,
                        thumbnail = entry.thumbnail ?: artist?.artist?.thumbnailUrl,
                        shape = CircleShape,
                        onOpen = { navController.navigate("artist/${entry.id}") },
                        onRestore = { ReleaseFilter.unignoreArtist(context, entry.id) },
                    )
                }
            }
            if (releases.orEmpty().isNotEmpty()) {
                item(key = "releases") { ListSectionHeader(stringResource(R.string.arc_hidden_releases)) }
                items(releases.orEmpty(), key = { "release_${it.id}" }) { entry ->
                    IgnoredRow(
                        entry = entry,
                        thumbnail = entry.thumbnail,
                        shape = RoundedCornerShape(10.dp),
                        onOpen = { navController.navigate("album/${entry.id}") },
                        onRestore = { ReleaseFilter.unhideRelease(context, entry.id) },
                    )
                }
            }
        }
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
    }
}

@Composable
private fun IgnoredRow(entry: ReleaseFilter.Entry, thumbnail: String?, shape: Shape, onOpen: () -> Unit, onRestore: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onRestore) { Text(stringResource(R.string.arc_show_again)) }
    }
}
