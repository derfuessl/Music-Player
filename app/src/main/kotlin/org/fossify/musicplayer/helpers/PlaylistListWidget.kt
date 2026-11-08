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
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_playlist_rows)
    }

    private fun sizedViews(context: Context, id: Int, options: Bundle): RemoteViews {
        val sizes = PlaylistWidgetSize(context, tiled)
        val prefs = preferences(context)
        val heading = prefs.getBoolean("heading:$id", false)
        fun build(size: android.util.SizeF) = panel(context, id, sizes.grid(size, heading), heading)
        val exact = sizes.exact(options)
        if (Build.VERSION.SDK_INT >= 31 && exact.isNotEmpty()) {
            return RemoteViews(exact.associateWith(::build))
        }
        return RemoteViews(build(sizes.legacy(options, true)), build(sizes.legacy(options, false)))
    }

    private fun panel(context: Context, id: Int, grid: PlaylistWidgetGrid, heading: Boolean): RemoteViews {
        val prefs = preferences(context)
        val color = prefs.getInt("color:$id", DEFAULT_COLOR)
        val transparency = prefs.getInt("transparency:$id", 0)
        val identity = Uri.parse("playlist-widget://instance/$id")
        return RemoteViews(context.packageName, R.layout.widget_playlist_list).apply {
            // Extras alone do not distinguish adapter factories. Include the geometry in
            // the identity so a rotation or resize cannot reuse stale column counts.
            val adapter = Intent(context, PlaylistListService::class.java)
                .setData(Uri.withAppendedPath(identity, "${grid.columns}-${grid.capacity}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(TILED, tiled).putExtra(COLUMNS, grid.columns).putExtra(CAPACITY, grid.capacity)
            removeAllViews(R.id.widget_playlist_collection)
            addView(R.id.widget_playlist_collection, RemoteViews(context.packageName, R.layout.widget_playlist_collection))
            setRemoteAdapter(R.id.widget_playlist_rows, adapter)
            setTextViewText(R.id.widget_playlist_empty, context.getString(
                if (grid.capacity == 0) R.string.widget_enlarge else R.string.widget_playlist_empty))
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
        internal const val COLUMNS = "playlist_widget_columns"
        internal const val CAPACITY = "playlist_widget_capacity"
        const val DEFAULT_COLOR = 0xFFF6F5FC.toInt()
        fun textColor(color: Int) = if (ColorUtils.calculateLuminance(color) > 0.179) Color.BLACK else Color.WHITE
        fun preferences(context: Context) = context.getSharedPreferences("playlist_list_widgets", Context.MODE_PRIVATE)
        // The host can re-inflate old RemoteViews with a new system font size. Recalculate
        // capacity as well, even if the previous app process was killed during that change.
        fun refreshMetrics(context: Context) {
            val config = context.resources.configuration
            val signature = "${config.densityDpi}:${config.fontScale}:${config.locales.toLanguageTags()}"
            val prefs = preferences(context)
            if (prefs.getString("layoutMetrics", null) == signature) return
            prefs.edit().putString("layoutMetrics", signature).apply()
            val manager = AppWidgetManager.getInstance(context)
            for (provider in listOf(PlaylistListWidget(), PlaylistTileWidget())) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider.javaClass))
                if (ids.isNotEmpty()) provider.onUpdate(context, manager, ids)
            }
        }

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
 * complete rows visible. Repeated host requests reuse the page until its data changes. */
class PlaylistListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val tiled = intent.getBooleanExtra(PlaylistListWidget.TILED, false)
        val columns = intent.getIntExtra(PlaylistListWidget.COLUMNS, 1).coerceIn(1, PlaylistListWidget.LIMIT)
        val capacity = intent.getIntExtra(PlaylistListWidget.CAPACITY, 1).coerceIn(0, PlaylistListWidget.LIMIT)
        val prefs = PlaylistListWidget.preferences(this)
        return object : RemoteViewsFactory {
            private var items: List<Playlist> = emptyList()
            private var cachedPage: RemoteViews? = null
            override fun onCreate() {}
            override fun onDestroy() { cachedPage = null; items = emptyList() }
            override fun onDataSetChanged() {
                // Reload only visible rows and artwork. The twelve stored IDs stay intact.
                cachedPage = null
                val selected = prefs.getStringSet(widgetId.toString(), null)
                items = when {
                    capacity == 0 -> emptyList()
                    selected == null -> playlistDAO.widgetPlaylists(capacity)
                    selected.isEmpty() -> emptyList()
                    else -> playlistDAO.widgetPlaylistsById(selected.mapNotNull(String::toIntOrNull), capacity)
                }
            }

            override fun getCount() = if (items.isEmpty()) 0 else 1
            override fun getViewTypeCount() = 1
            override fun hasStableIds() = true
            override fun getItemId(position: Int) = position.toLong()
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewAt(position: Int): RemoteViews? {
                if (position != 0 || items.isEmpty()) return null
                cachedPage?.let { return it }
                val color = PlaylistListWidget.textColor(prefs.getInt("color:$widgetId", PlaylistListWidget.DEFAULT_COLOR))
                return RemoteViews(packageName, R.layout.widget_playlist_page).apply {
                    removeAllViews(R.id.widget_playlist_page)
                    for (row in items.chunked(columns)) {
                        val band = RemoteViews(packageName, R.layout.widget_playlist_band)
                        band.removeAllViews(R.id.widget_playlist_band)
                        row.forEach { band.addView(R.id.widget_playlist_band, cell(it, tiled, color)) }
                        addView(R.id.widget_playlist_page, band)
                    }
                }.also { cachedPage = it }
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
