package com.metrolist.music.ui.screens.settings

import com.metrolist.music.arc.arcScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.metrolist.music.arc.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.arc.Addons
import com.metrolist.music.arc.ArcAddonsOnKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

/** Music source add-ons (Beta): added by address, tried before YouTube for every song while switched on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (enabled, onEnabledChange) = rememberPreference(ArcAddonsOnKey, defaultValue = false)
    val addons by remember { Addons.list(context) }.collectAsState(initial = emptyList())
    var address by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Material3SettingsGroup(
            title = stringResource(R.string.arc_beta),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.arc_addons_on)) },
                        description = { Text(stringResource(R.string.arc_addons_on_desc)) },
                        trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) },
                        onClick = { onEnabledChange(!enabled) },
                    ),
                ),
        )
        Spacer(Modifier.height(16.dp))
        if (addons.isNotEmpty()) {
            Material3SettingsGroup(
                title = stringResource(R.string.arc_addons),
                items =
                    addons.map { addon ->
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.integration),
                            title = { Text(addon.name) },
                            description = { Text(addon.base) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = addon.enabled, onCheckedChange = { on -> scope.launch { Addons.setEnabled(context, addon, on) } })
                                    androidx.compose.material3.IconButton(onClick = { scope.launch { Addons.remove(context, addon) } }) {
                                        Icon(painterResource(R.drawable.delete), contentDescription = null)
                                    }
                                }
                            },
                        )
                    },
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
                error = null
            },
            label = { Text(stringResource(R.string.arc_addon_address)) },
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: stringResource(R.string.arc_addon_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = address.isNotBlank() && !adding,
                onClick = {
                    adding = true
                    scope.launch {
                        Addons.add(context, address)
                            .onSuccess { address = "" }
                            .onFailure { error = it.message }
                        adding = false
                    }
                },
            ) { Text(stringResource(R.string.arc_addon_add)) }
        }
        Text(
            text = stringResource(R.string.arc_addons_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.arc_addons)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
