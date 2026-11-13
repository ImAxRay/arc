package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArcWidgetEntryPoint {
    fun arcWidget(): ArcWidgetManager
}

/** Draws the Arc widget when it is added or resized; taps on it go straight to MusicService. */
class MusicWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = render(context)

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        render(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = render(context)

    private fun render(context: Context) {
        val pending = goAsync()
        val widget = EntryPointAccessors.fromApplication(context.applicationContext, ArcWidgetEntryPoint::class.java).arcWidget()
        scope.launch {
            runCatching { widget.render() }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.metrolist.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.metrolist.music.widget.NEXT"
        const val ACTION_PREVIOUS = "com.metrolist.music.widget.PREVIOUS"
        const val ACTION_PLAY_TILE = "com.metrolist.music.widget.PLAY_TILE"
        const val EXTRA_TILE = "tile"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
