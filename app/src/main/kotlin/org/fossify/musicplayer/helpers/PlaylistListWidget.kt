package org.fossify.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.ColorUtils
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.PlaylistWidgetActionActivity
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.models.Playlist

/** Settings belong to the launcher instance. No knowledge of a neighbouring widget is needed. */
open class PlaylistListWidget : AppWidgetProvider() {
    protected open val tiled = false

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val views = sizedViews(context, id, options)
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_playlist_rows)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, sizedViews(context, id, options))
    }

    private fun sizedViews(context: Context, id: Int, options: Bundle): RemoteViews {
        // Modern launchers supply the actual size for each orientation. Older ones supply
        // the extrema, where portrait is the narrower and taller of the two layouts.
        if (Build.VERSION.SDK_INT >= 31) {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty().distinct().filter { it.width > 0 && it.height > 0 }
            if (sizes.size in 1..16) {
                return RemoteViews(sizes.associateWith { panel(context, id, it.width.toInt()) })
            }
        }
        val narrow = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val wide = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, narrow)
        return RemoteViews(panel(context, id, wide), panel(context, id, narrow))
    }

    private fun panel(context: Context, id: Int, width: Int): RemoteViews {
        val prefs = preferences(context)
        val heading = prefs.getBoolean("heading:$id", false)
        val color = prefs.getInt("color:$id", DEFAULT_COLOR)
        val transparency = prefs.getInt("transparency:$id", 0)
        val identity = Uri.parse("playlist-widget://instance/$id")
        return RemoteViews(context.packageName, R.layout.widget_playlist_list).apply {
            // Extras alone do not distinguish adapter factories. Include the geometry in
            // the identity so a rotation or resize cannot reuse stale column counts.
            val adapter = Intent(context, PlaylistListService::class.java)
                .setData(Uri.withAppendedPath(identity, width.toString()))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(TILED, tiled).putExtra(WIDTH, width)
            setRemoteAdapter(R.id.widget_playlist_rows, adapter)
            setEmptyView(R.id.widget_playlist_rows, R.id.widget_playlist_empty)
            setInt(R.id.widget_playlist_panel, "setColorFilter", color)
            setInt(R.id.widget_playlist_panel, "setImageAlpha", (100 - transparency) * 255 / 100)
            setViewVisibility(R.id.widget_playlist_heading, if (heading) View.VISIBLE else View.GONE)
            setTextColor(R.id.widget_playlist_heading, textColor(color))
            setTextColor(R.id.widget_playlist_empty, textColor(color))
            val open = Intent(context, PlaylistWidgetActionActivity::class.java).setData(identity)
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            setPendingIntentTemplate(R.id.widget_playlist_rows, PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(it.toString()).remove("color:$it").remove("transparency:$it").remove("heading:$it") }
        editor.apply()
    }

    companion object {
        const val LIMIT = PlaylistWidgetGrid.LIMIT
        internal const val TILED = "playlist_widget_tiled"
        internal const val WIDTH = "playlist_widget_width"
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

class PlaylistTileWidget : PlaylistListWidget() {
    override val tiled = true
}

/** Android binds the factory on demand. Returning a single, non-flipping page keeps all
 * playlists visible and lets the launcher measure each weighted row against its real bounds. */
class PlaylistListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val tiled = intent.getBooleanExtra(PlaylistListWidget.TILED, false)
        val width = intent.getIntExtra(PlaylistListWidget.WIDTH, 180)
        val prefs = PlaylistListWidget.preferences(this)
        return object : RemoteViewsFactory {
            private var items: List<Playlist> = emptyList()
            override fun onCreate() {}
            override fun onDestroy() { items = emptyList() }
            override fun onDataSetChanged() {
                val selected = prefs.getStringSet(widgetId.toString(), null)
                items = when {
                    selected == null -> playlistDAO.widgetPlaylists(PlaylistListWidget.LIMIT)
                    selected.isEmpty() -> emptyList()
                    else -> playlistDAO.widgetPlaylistsById(selected.mapNotNull(String::toIntOrNull), PlaylistListWidget.LIMIT)
                }
            }

            override fun getCount() = if (items.isEmpty()) 0 else 1
            override fun getViewTypeCount() = 1
            override fun hasStableIds() = true
            override fun getItemId(position: Int) = position.toLong()
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewAt(position: Int): RemoteViews? {
                if (position != 0 || items.isEmpty()) return null
                val columns = PlaylistWidgetGrid.columns(tiled, width)
                val color = PlaylistListWidget.textColor(prefs.getInt("color:$widgetId", PlaylistListWidget.DEFAULT_COLOR))
                return RemoteViews(packageName, R.layout.widget_playlist_page).apply {
                    removeAllViews(R.id.widget_playlist_page)
                    for (row in items.chunked(columns)) {
                        val band = RemoteViews(packageName, R.layout.widget_playlist_band)
                        band.removeAllViews(R.id.widget_playlist_band)
                        row.forEach { band.addView(R.id.widget_playlist_band, cell(it, tiled, color)) }
                        // Reserve unused columns in the final row so text and covers stay aligned.
                        repeat(columns - row.size) {
                            band.addView(R.id.widget_playlist_band, RemoteViews(packageName, R.layout.widget_playlist_spacer))
                        }
                        addView(R.id.widget_playlist_page, band)
                    }
                }
            }
        }
    }

    private fun cell(playlist: Playlist, tiled: Boolean, color: Int): RemoteViews {
        return RemoteViews(packageName, if (tiled) R.layout.widget_playlist_tile else R.layout.widget_playlist_row).apply {
            setTextViewText(R.id.widget_playlist_name, playlist.title)
            setTextColor(R.id.widget_playlist_name, color)
            val art = PlaylistArtwork.loadPlaylist(applicationContext, playlist.id)
            if (art == null) setImageViewResource(R.id.widget_playlist_picture, R.drawable.ic_playlist_vector)
            else {
                // All twelve images travel in one page. Bound their combined bitmap footprint.
                val pixels = if (tiled) 128 else 80
                setImageViewBitmap(R.id.widget_playlist_picture, Bitmap.createScaledBitmap(art, pixels, pixels, true))
            }
            val open = Intent().putExtra(PLAYLIST, playlist.id)
            setOnClickFillInIntent(R.id.widget_playlist_name, open)
            setOnClickFillInIntent(R.id.widget_playlist_picture, Intent(open).putExtra(PlaylistWidgetActionActivity.PLAY, true))
            setContentDescription(R.id.widget_playlist_picture, getString(R.string.widget_play_named_playlist, playlist.title))
        }
    }
}
