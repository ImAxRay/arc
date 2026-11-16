package com.metrolist.music.arc.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.arc.CapsLabel

private val CardHeight = 470.dp

/**
 * Add to Home: modules sorted into groups, one card each, swiped through like a ship picker in a roguelike: how it
 * looks, what it does, its options, and a button to put it on Home. [shown] is read here, so cards follow it.
 */
@Composable
fun ModulePicker(env: HomeEnv, modules: List<HomeModule>, shown: () -> Set<String>, onAdd: (String) -> Unit) {
    val groups = remember(modules) { ModuleGroup.entries.filter { group -> modules.any { it.group == group } } }
    var group by remember { mutableStateOf(groups.firstOrNull()) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        CapsLabel(stringResource(R.string.arc_home_add_module), Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(groups) { g -> FilterChip(selected = g == group, onClick = { group = g }, label = { Text(stringResource(g.title)) }) }
        }
        val list = modules.filter { it.group == group }
        key(group) {
            val pager = rememberPagerState { list.size }
            HorizontalPager(
                state = pager,
                // live previews are heavy: composing the next one as a swipe lets go stalls the settle, so keep it ready
                beyondViewportPageCount = 1,
                flingBehavior = rememberPageFling(pager),
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = 12.dp),
            ) { page ->
                val module = list[page]
                ModuleCard(env, module, onHome = module.key in shown(), onAdd = { onAdd(module.key) })
            }
            if (list.size > 1) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { PageDots(pager) }
        }
    }
}

@Composable
private fun ModuleCard(env: HomeEnv, module: HomeModule, onHome: Boolean, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val context = LocalContext.current
    // one height for every card: a pager that resizes to each page jolts the sheet once a swipe settles
    Column(Modifier.fillMaxWidth().height(CardHeight).border(1.dp, colors.outlineVariant, shape).padding(bottom = 16.dp)) {
        ModuleLivePreview(env, module, height = 200.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(module.name(context), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            Text(module.description(context), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            ModuleOptions(module, nameSwitch = false)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAdd, enabled = !onHome) {
                    if (!onHome) Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (onHome) R.string.arc_home_on_home else R.string.arc_home_add_module))
                }
            }
        }
    }
}
