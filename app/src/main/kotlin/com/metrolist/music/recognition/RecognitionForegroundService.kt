package com.metrolist.music.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class RecognitionForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var recognitionJob: Job? = null
    private var statusJob: Job? = null
    private var keepNotificationOnStop = false
    private var terminalStateHandled = false

    private val imageLoader by lazy { ImageLoader.Builder(this).build() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Timber.tag(TAG).d("onStartCommand: action=%s", intent?.action)
        if (recognitionJob?.isActive != true && startInForeground()) startRecognition()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recognitionJob?.cancel()
        statusJob?.cancel()
        serviceScope.cancel()
        if (!keepNotificationOnStop) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        val notification =
            buildNotification(
                title = getString(R.string.recognize_music),
                contentText = getString(R.string.recognition_notification_listening),
                isTerminal = false,
                contentIntent = null,
                largeIcon = null,
                actionIntent = null,
                actionTitle = null,
            )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return true
        } catch (exception: SecurityException) {
            Timber.w(exception, "Unable to start microphone foreground service")
        } catch (exception: RuntimeException) {
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                exception::class.java.name != "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                throw exception
            }
            Timber.w(exception, "Unable to start microphone foreground service")
        }
        stopSelf()
        return false
    }

    private fun startRecognition() {
        if (recognitionJob?.isActive == true) return
        keepNotificationOnStop = false
        terminalStateHandled = false
        MusicRecognitionService.reset()

        statusJob?.cancel()
        statusJob =
            serviceScope.launch {
                MusicRecognitionService.recognitionStatus.collect { status ->
                    if (status !is RecognitionStatus.Ready) renderStatus(status)
                }
            }
        recognitionJob =
            serviceScope.launch {
                val result = MusicRecognitionService.recognize(this@RecognitionForegroundService)
                if (
                    result is RecognitionStatus.Error &&
                    MusicRecognitionService.recognitionStatus.value !is RecognitionStatus.Error
                ) {
                    renderStatus(result)
                }
            }
    }

    private fun renderStatus(status: RecognitionStatus) {
        when (status) {
            is RecognitionStatus.Listening -> {
                updateNotification(
                    getString(R.string.recognize_music),
                    getString(R.string.recognition_notification_listening),
                )
            }

            is RecognitionStatus.Processing -> {
                updateNotification(
                    getString(R.string.recognize_music),
                    getString(R.string.recognition_notification_processing),
                )
            }

            is RecognitionStatus.Success -> {
                handleSuccess(status.result)
            }

            is RecognitionStatus.NoMatch -> {
                finishWithMessage(R.string.recognition_notification_no_match)
            }

            is RecognitionStatus.Error -> {
                finishWithMessage(R.string.recognition_notification_failed)
            }

            is RecognitionStatus.Ready -> {
                return
            }
        }
    }

    private fun finishWithMessage(message: Int) {
        if (terminalStateHandled) return
        terminalStateHandled = true
        updateNotification(
            getString(R.string.recognize_music),
            getString(message),
            isTerminal = true,
        )
        finishWithPersistentResult()
    }

    private fun handleSuccess(result: RecognitionResult) {
        if (terminalStateHandled) return
        terminalStateHandled = true
        val pendingIntent = createResultPendingIntent(result)
        updateNotification(
            result.title,
            result.artist,
            isTerminal = true,
            contentIntent = pendingIntent,
            actionIntent = pendingIntent,
            actionTitle = getString(R.string.listen_on_metrolist),
        )

        serviceScope.launch {
            val coverBitmap =
                (result.coverArtHqUrl ?: result.coverArtUrl)?.let { url ->
                    withTimeoutOrNull(1_500L) { loadBitmap(url) }
                }
            if (coverBitmap != null) {
                updateNotification(
                    result.title,
                    result.artist,
                    isTerminal = true,
                    contentIntent = pendingIntent,
                    largeIcon = coverBitmap,
                    actionIntent = pendingIntent,
                    actionTitle = getString(R.string.listen_on_metrolist),
                )
            }
            finishWithPersistentResult()
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                imageLoader
                    .execute(
                        ImageRequest
                            .Builder(this@RecognitionForegroundService)
                            .data(url)
                            .size(200, 200)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull()
        }

    private fun updateNotification(
        title: String,
        contentText: String,
        isTerminal: Boolean = false,
        contentIntent: PendingIntent? = null,
        largeIcon: Bitmap? = null,
        actionIntent: PendingIntent? = null,
        actionTitle: String? = null,
    ) {
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification(
                title,
                contentText,
                isTerminal,
                contentIntent,
                largeIcon,
                actionIntent,
                actionTitle,
            ),
        )
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        isTerminal: Boolean,
        contentIntent: PendingIntent?,
        largeIcon: Bitmap?,
        actionIntent: PendingIntent?,
        actionTitle: String?,
    ) = NotificationCompat
        .Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.mic)
        .setContentTitle(title)
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOnlyAlertOnce(true)
        .setOngoing(!isTerminal)
        .setAutoCancel(isTerminal)
        .setContentIntent(contentIntent)
        .setLargeIcon(largeIcon)
        .apply {
            if (actionIntent != null && actionTitle != null) addAction(0, actionTitle, actionIntent)
        }.build()

    private fun createResultPendingIntent(result: RecognitionResult) =
        PendingIntent.getActivity(
            this,
            RESULT_PENDING_INTENT_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(EXTRA_RECOGNITION_TRACK_ID, result.trackId)
                putExtra(EXTRA_RECOGNITION_TITLE, result.title)
                putExtra(EXTRA_RECOGNITION_ARTIST, result.artist)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun finishWithPersistentResult() {
        keepNotificationOnStop = true
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recognition_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.recognition_notification_channel_desc)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val EXTRA_RECOGNITION_TRACK_ID = "recognition_track_id"
        const val EXTRA_RECOGNITION_TITLE = "recognition_title"
        const val EXTRA_RECOGNITION_ARTIST = "recognition_artist"

        private const val CHANNEL_ID = "recognition_channel"
        private const val NOTIFICATION_ID = 9100
        private const val RESULT_PENDING_INTENT_REQUEST_CODE = 9101
        private const val TAG = "RecognitionFgService"
    }
}
