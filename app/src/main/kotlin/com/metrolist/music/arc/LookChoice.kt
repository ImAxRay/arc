package com.metrolist.music.arc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val LookAskedKey = booleanPreferencesKey("arcLookAsked")

/** Asked once on first start: smoother sets every heavy visual off, prettier sets them on. Closing it keeps the defaults. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstLookChoice() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = context.dataStore.data.first()[LookAskedKey] != true }
    if (!show) return

    fun choose(pretty: Boolean?) {
        show = false
        scope.launch {
            context.dataStore.edit {
                it[LookAskedKey] = true
                if (pretty != null) {
                    it[ArcHomeBackgroundKey] = (if (pretty) HomeBackground.FIELD else HomeBackground.OFF).name
                    it[ArcTiltCoversKey] = pretty
                    it[ArcMotionCoversKey] = pretty
                    it[ArcPressGlowKey] = pretty
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { choose(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.arc_look_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.arc_look_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LookCard(HomeBackground.OFF, R.string.arc_look_smooth, R.string.arc_look_smooth_desc, Modifier.weight(1f)) { choose(false) }
                LookCard(HomeBackground.FIELD, R.string.arc_look_pretty, R.string.arc_look_pretty_desc, Modifier.weight(1f)) { choose(true) }
            }
        }
    }
}

@Composable
private fun LookCard(backdrop: HomeBackground, title: Int, desc: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .tapBounce()
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.background),
        ) {
            HomeBackdrop(backdrop, colors.background, colors.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
