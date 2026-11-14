package com.metrolist.music.arc.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R

@Composable
internal fun wrappedAvailable(env: HomeEnv): Boolean {
    val selectedChip by env.viewModel.selectedChip.collectAsStateWithLifecycle()
    return selectedChip == null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedModule(env: HomeEnv) {
    val show by env.viewModel.showWrappedCard.collectAsStateWithLifecycle()
    val wrappedState by env.viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = show) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (wrappedState.isDataReady) {
                    val bbhFont = FontFamily(Font(R.font.bbh_bartle_regular))
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.wrapped_ready_title),
                            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = bbhFont, textAlign = TextAlign.Center),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.wrapped_ready_subtitle),
                            style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { env.navController.navigate("wrapped") }) { Text(stringResource(R.string.open)) }
                    }
                } else {
                    ContainedLoadingIndicator()
                }
            }
        }
    }
}
