package com.metrolist.music.arc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK straight into a PackageInstaller session and installs it over this app.
 * Android shows its own confirm screen unless it lets an app update itself silently (Android 12+).
 */
object AppUpdate {
    sealed interface State {
        data object Idle : State

        /** [progress] is 0..1, or null while the size is unknown. */
        data class Downloading(val progress: Float?) : State

        data object Installing : State

        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val busy get() = _state.value is State.Downloading || _state.value is State.Installing

    fun start(context: Context, url: String) {
        if (busy) return
        val app = context.applicationContext
        _state.value = State.Downloading(null)
        scope.launch {
            val installer = app.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
            val sessionId = installer.createSession(params)
            runCatching {
                installer.openSession(sessionId).use { session ->
                    val connection = URL(url).openConnection() as HttpURLConnection
                    try {
                        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                        val total = connection.contentLengthLong
                        session.openWrite("arc.apk", 0, total).use { out ->
                            var done = 0L
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    done += read
                                    if (total > 0) _state.value = State.Downloading(done.toFloat() / total)
                                }
                            }
                            if (total > 0 && done != total) error("download cut short")
                            session.fsync(out)
                        }
                    } finally {
                        connection.disconnect()
                    }
                    _state.value = State.Installing
                    // The system fills the status into this intent, so it has to be mutable.
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(app, sessionId, Intent(app, AppUpdateReceiver::class.java), flags)
                    session.commit(status.intentSender)
                }
            }.onFailure {
                runCatching { installer.abandonSession(sessionId) }
                _state.value = State.Failed(it.message ?: it.javaClass.simpleName)
            }
        }
    }

    internal fun finished(failure: String?) {
        _state.value = if (failure == null) State.Idle else State.Failed(failure)
    }
}

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdate.finished(null)
            else -> AppUpdate.finished(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status")
        }
    }
}

/** A progress bar and a line of text while an update downloads or installs, the reason when it failed, nothing when idle. */
@Composable
fun AppUpdateProgress(modifier: Modifier = Modifier) {
    val state by AppUpdate.state.collectAsState()
    val text =
        when (val s = state) {
            AppUpdate.State.Idle -> return
            is AppUpdate.State.Downloading ->
                s.progress?.let { stringResource(R.string.arc_update_downloading_percent, (it * 100).toInt()) }
                    ?: stringResource(R.string.arc_update_downloading)
            AppUpdate.State.Installing -> stringResource(R.string.arc_update_installing)
            is AppUpdate.State.Failed -> stringResource(R.string.arc_update_failed, s.reason)
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val s = state) {
            is AppUpdate.State.Downloading ->
                s.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            AppUpdate.State.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> {}
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (state is AppUpdate.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
