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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.metrolist.music.arc.ArcUntitledOnKey
import com.metrolist.music.arc.TopAppBar
import com.metrolist.music.arc.UntitledMusic
import com.metrolist.music.arc.arcScroll
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
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(72.dp))
        Text(stringResource(R.string.arc_beta).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.arc_untitled_intro), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.arc_untitled_switch), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = on, onCheckedChange = onChange)
        }
        Spacer(Modifier.height(16.dp))
        if (!signedIn) {
            Text(stringResource(R.string.arc_untitled_sign_in_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(enabled = on, onClick = { signingIn = true }) { Text(stringResource(R.string.arc_untitled_sign_in)) }
        } else {
            Row {
                Button(enabled = on && !busy, onClick = ::syncNow) { Text(stringResource(R.string.arc_untitled_sync)) }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { scope.launch { UntitledMusic.signOut(context, database) } }) {
                    Text(stringResource(R.string.arc_untitled_sign_out))
                }
            }
        }
        if (busy) CircularProgressIndicator(Modifier.padding(16.dp))
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.arc_untitled_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    TopAppBar(
        title = { Text(stringResource(R.string.arc_untitled)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) { Icon(painterResource(R.drawable.arrow_back), null) }
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
