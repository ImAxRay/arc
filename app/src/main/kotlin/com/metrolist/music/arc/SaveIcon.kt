package com.metrolist.music.arc

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R

/** Albums and playlists are saved with a bookmark; the heart stays for liking songs. */
@Composable
fun SaveIcon(saved: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (saved) R.drawable.bookmark else R.drawable.bookmark_border),
        tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        contentDescription = stringResource(if (saved) R.string.arc_saved else R.string.arc_save),
        modifier = modifier,
    )
}
