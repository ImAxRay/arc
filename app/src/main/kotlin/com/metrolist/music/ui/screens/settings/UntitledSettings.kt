package com.metrolist.music.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.arc.ArcUntitledNoteDismissedKey
import com.metrolist.music.arc.ArcUntitledOnKey
import com.metrolist.music.arc.TopAppBar
import com.metrolist.music.arc.UntitledMusic
import com.metrolist.music.arc.arcScroll
import com.metrolist.music.arc.switchItem
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

/** Untitled as a music source (Beta): sign in on Untitled's own page, then its projects show up in the library. */
@Composable
fun UntitledSettings(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val signedIn by remember { UntitledMusic.signedIn(context) }.collectAsState(initial = false)
    val (on, onChange) = rememberPreference(ArcUntitledOnKey, true)
    var noteDismissed by rememberPreference(ArcUntitledNoteDismissedKey, false)
    var signingIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun syncNow() {
        busy = true
        message = null
        scope.launch {
            message = runCatching { UntitledMusic.sync(context, database, force = true) }
                .fold({ context.getString(R.string.arc_untitled_synced, it) }, { it.message })
            busy = false
        }
    }

    if (signingIn) {
        UntitledSignIn(
            onSignedIn = {
                signingIn = false
                syncNow()
            },
            onCancel = { signingIn = false },
        )
        return
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .arcScroll()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        // same notice as Discord's
        AnimatedVisibility(visible = !noteDismissed) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.warning), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.arc_untitled_note), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { noteDismissed = true }, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }
        }
        Material3SettingsGroup(
            title = stringResource(R.string.arc_untitled) + " · " + stringResource(R.string.arc_beta),
            icon = R.drawable.cloud,
            items = listOf(
                switchItem(R.drawable.cloud, R.string.arc_untitled_switch, R.string.arc_untitled_intro, on, onChange),
            ),
        )

        Spacer(Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.account),
            icon = R.drawable.account,
            items = if (!signedIn) {
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.login),
                        title = { Text(stringResource(R.string.arc_untitled_sign_in)) },
                        description = { Text(stringResource(R.string.arc_untitled_sign_in_hint)) },
                        enabled = on,
                        onClick = { signingIn = true },
                    ),
                )
            } else {
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text(stringResource(R.string.arc_untitled_sync)) },
                        description = message?.let { { Text(it) } },
                        trailingContent = if (busy) { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } } else null,
                        enabled = on && !busy,
                        onClick = ::syncNow,
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.arc_untitled_sign_out)) },
                        onClick = { scope.launch { UntitledMusic.signOut(context, database) } },
                    ),
                )
            },
        )
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.arc_untitled)) },
        navigationIcon = {
            com.metrolist.music.ui.component.IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), null)
            }
        },
    )
}

/** Untitled's own sign-in page; once it lands in the library, the session it left in the cookies is kept. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun UntitledSignIn(onSignedIn: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))) {
        AndroidView(
            factory = {
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val inLibrary = url.startsWith(UntitledMusic.SITE + "/library")
                            if (inLibrary) {
                                scope.launch {
                                    CookieManager.getInstance().flush()
                                    if (UntitledMusic.keepSession(context)) onSignedIn()
                                }
                            }
                        }
                    }
                    loadUrl(UntitledMusic.SIGN_IN_PAGE)
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = 56.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.close), null) }
            Text(stringResource(R.string.arc_untitled_sign_in), style = MaterialTheme.typography.titleMedium)
        }
    }
}
