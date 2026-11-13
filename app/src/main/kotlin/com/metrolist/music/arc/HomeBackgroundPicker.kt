package com.metrolist.music.arc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

/** Picks the Home background while showing it live on a small Home stand-in; a choice applies at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBackgroundPicker(
    current: HomeBackground,
    onSelect: (HomeBackground) -> Unit,
    onDismiss: () -> Unit,
    label: @Composable (HomeBackground) -> String,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.arc_home_background), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            val shape = RoundedCornerShape(28.dp)
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier
                    .width(180.dp)
                    .aspectRatio(9f / 19f)
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, colors.outlineVariant, shape),
            ) {
                HomeBackdrop(current, colors.background, colors.primary)
                // faint stand-ins for Home's tiles, so the strength reads as it will behind real content
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.height(20.dp))
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(2) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.onSurface.copy(alpha = 0.06f)),
                                )
                            }
                        }
                    }
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.onSurface.copy(alpha = 0.05f)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeBackground.entries.forEach {
                    FilterChip(selected = it == current, onClick = { onSelect(it) }, label = { Text(label(it)) })
                }
            }
        }
    }
}
