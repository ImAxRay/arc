package com.metrolist.music.arc.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.NavigationTitle

/** "Similar to" rows, one per seed from the library. */
@Composable
fun SimilarToModule(env: HomeEnv) {
    val recommendations by env.viewModel.similarRecommendations.collectAsStateWithLifecycle()
    Column {
        recommendations.orEmpty().forEachIndexed { index, recommendation ->
            key(index, recommendation.title.id) {
                NavigationTitle(
                    label = stringResource(R.string.similar_to),
                    title = recommendation.title.title,
                    thumbnail =
                        recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                            {
                                val shape = if (recommendation.title is Artist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(shape))
                            }
                        },
                    onClick = {
                        when (val seed = recommendation.title) {
                            is Song -> seed.album?.let { env.navController.navigate("album/${it.id}") }
                            is Album -> env.navController.navigate("album/${seed.id}")
                            is Artist -> env.navController.navigate("artist/${seed.id}")
                            is Playlist -> {}
                        }
                    },
                )
                HomeYtRow(env, recommendation.items, "home_similar_$index")
            }
        }
    }
}
