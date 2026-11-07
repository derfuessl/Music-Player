package org.fossify.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.fossify.musicplayer.R
import android.graphics.Color
import android.view.View
import androidx.core.graphics.ColorUtils
import org.fossify.musicplayer.activities.PlaylistWidgetActionActivity
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Playlist

/** Each home-screen instance owns its selection. A missing preference means automatic selection. */
open class PlaylistListWidget : AppWidgetProvider() {
    protected open val tiled = false
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, if (tiled) R.layout.widget_playlist_grid else R.layout.widget_playlist_list)
            // Android compares intents without their extras when sharing factories or PendingIntents.
            // Distinct data URIs keep two adjacent lists and their configuration buttons independent.
            val identity = Uri.parse("playlist-widget://instance/$id")
            val adapter = Intent(context, PlaylistListService::class.java).setData(identity)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(TILED, tiled)
            views.setRemoteAdapter(R.id.widget_playlist_rows, adapter)
            views.setEmptyView(R.id.widget_playlist_rows, R.id.widget_playlist_empty)
            val prefs = preferences(context)
            val color = prefs.getInt("color:$id", DEFAULT_COLOR)
            val transparency = prefs.getInt("transparency:$id", 0)
            views.setInt(R.id.widget_playlist_panel, "setColorFilter", color)
            views.setInt(R.id.widget_playlist_panel, "setImageAlpha", (100 - transparency) * 255 / 100)
            views.setViewVisibility(R.id.widget_playlist_heading,
                if (prefs.getBoolean("heading:$id", false)) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_playlist_heading, textColor(color))
            views.setTextColor(R.id.widget_playlist_empty, textColor(color))
            // A collection has one template. Its two child targets supply the playlist ID and
            // desired action; the component stays pinned to our private widget entry point.
            val open = Intent(context, PlaylistWidgetActionActivity::class.java).setData(identity)
            val mutable = if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(R.id.widget_playlist_rows, PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable))
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_playlist_rows)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(it.toString()).remove("color:$it").remove("transparency:$it").remove("heading:$it") }
        editor.apply()
    }

    companion object {
        const val LIMIT = 6
        const val TILED = "playlist_widget_tiled"
        const val DEFAULT_COLOR = 0xFFF6F5FC.toInt()
        fun textColor(color: Int) = if (ColorUtils.calculateLuminance(color) > 0.179) Color.BLACK else Color.WHITE
        fun preferences(context: Context) = context.getSharedPreferences("playlist_list_widgets", Context.MODE_PRIVATE)
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (provider in listOf(PlaylistListWidget::class.java, PlaylistTileWidget::class.java)) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_playlist_rows)
            }
        }
    }
}

/** A distinct provider gives the launcher a separate entry. Settings and taps are still scoped to
 * the widget ID, so a grid and a list can use completely different selections on the same screen. */
class PlaylistTileWidget : PlaylistListWidget() {
    override val tiled = true
}

/** The launcher asks for rows on a worker thread. Only the small thumbnail crosses the process boundary. */
class PlaylistListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val tiled = intent.getBooleanExtra(PlaylistListWidget.TILED, false)
        return object : RemoteViewsFactory {
            private var items: List<Playlist> = emptyList()
            override fun onCreate() {}
            override fun onDestroy() { items = emptyList() }
            override fun onDataSetChanged() {
                val selected = PlaylistListWidget.preferences(this@PlaylistListService).getStringSet(widgetId.toString(), null)
                items = audioHelper.getAllPlaylists().sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
                    .filter { selected == null || it.id.toString() in selected }.take(PlaylistListWidget.LIMIT)
            }
            override fun getCount() = items.size
            override fun getViewTypeCount() = 1
            override fun hasStableIds() = true
            override fun getItemId(position: Int) = items.getOrNull(position)?.id?.toLong() ?: -1L
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewAt(position: Int): RemoteViews? {
                val playlist = items.getOrNull(position) ?: return null
                return RemoteViews(packageName, if (tiled) R.layout.widget_playlist_tile else R.layout.widget_playlist_row).apply {
                    setTextViewText(R.id.widget_playlist_name, playlist.title)
                    val color = PlaylistListWidget.preferences(this@PlaylistListService).getInt("color:$widgetId", PlaylistListWidget.DEFAULT_COLOR)
                    setTextColor(R.id.widget_playlist_name, PlaylistListWidget.textColor(color))
                    val art = PlaylistArtwork.loadPlaylist(applicationContext, playlist.id)
                    if (art == null) setImageViewResource(R.id.widget_playlist_picture, R.drawable.ic_playlist_vector)
                    else {
                        val pixels = if (tiled) 192 else 96
                        setImageViewBitmap(R.id.widget_playlist_picture, Bitmap.createScaledBitmap(art, pixels, pixels, true))
                    }
                    val open = Intent().putExtra(PLAYLIST, playlist.id)
                    setOnClickFillInIntent(R.id.widget_playlist_name, open)
                    setOnClickFillInIntent(R.id.widget_playlist_picture, Intent(open).putExtra(PlaylistWidgetActionActivity.PLAY, true))
                    setContentDescription(R.id.widget_playlist_picture, getString(R.string.widget_play_named_playlist, playlist.title))
                }
            }
        }
    }
}
