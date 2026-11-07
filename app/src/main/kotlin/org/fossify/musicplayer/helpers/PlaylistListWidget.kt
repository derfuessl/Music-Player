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
import com.google.gson.Gson
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.PlaylistWidgetSelectionActivity
import org.fossify.musicplayer.activities.TracksActivity
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Playlist

/** Each home-screen instance owns its selection. A missing preference means automatic selection. */
class PlaylistListWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_playlist_list)
            // Android compares intents without their extras when sharing factories or PendingIntents.
            // Distinct data URIs keep two adjacent lists and their configuration buttons independent.
            val identity = Uri.parse("playlist-widget://instance/$id")
            val adapter = Intent(context, PlaylistListService::class.java).setData(identity)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setRemoteAdapter(R.id.widget_playlist_rows, adapter)
            views.setEmptyView(R.id.widget_playlist_rows, R.id.widget_playlist_empty)
            val configure = Intent(context, PlaylistWidgetSelectionActivity::class.java).setData(identity)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setOnClickPendingIntent(R.id.widget_playlist_choose, PendingIntent.getActivity(context, id, configure,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            // Only this explicit TracksActivity template accepts the row's playlist extra.
            val open = Intent(context, TracksActivity::class.java).setData(identity)
            val mutable = if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(R.id.widget_playlist_rows, PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable))
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_playlist_rows)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(it.toString()) }
        editor.apply()
    }

    companion object {
        const val LIMIT = 6
        fun preferences(context: Context) = context.getSharedPreferences("playlist_list_widgets", Context.MODE_PRIVATE)
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaylistListWidget::class.java))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_playlist_rows)
        }
    }
}

/** The launcher asks for rows on a worker thread. Only the small thumbnail crosses the process boundary. */
class PlaylistListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
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
                return RemoteViews(packageName, R.layout.widget_playlist_row).apply {
                    setTextViewText(R.id.widget_playlist_name, playlist.title)
                    val art = PlaylistArtwork.loadPlaylist(applicationContext, playlist.id)
                    if (art == null) setImageViewResource(R.id.widget_playlist_picture, R.drawable.ic_playlist_vector)
                    else setImageViewBitmap(R.id.widget_playlist_picture, Bitmap.createScaledBitmap(art, 96, 96, true))
                    setOnClickFillInIntent(R.id.widget_playlist_row, Intent().putExtra(PLAYLIST, Gson().toJson(playlist)))
                }
            }
        }
    }
}
