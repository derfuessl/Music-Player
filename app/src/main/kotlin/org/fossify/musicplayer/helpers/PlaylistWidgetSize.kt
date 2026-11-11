package org.fossify.musicplayer.helpers

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.LayoutInflater
import android.view.View
import org.fossify.musicplayer.R
import kotlin.math.roundToInt

/** The launcher reports usable bounds in dp, not its private home-screen row/column spans.
 * Measure the actual XML once per update so system font scaling and two-line labels use the
 * same space in the picker and on the home screen, including on launchers predating Android 12. */
internal class PlaylistWidgetSize(private val context: Context, private val tiled: Boolean, private val large: Boolean = false) {
    private val resources = context.resources
    private val density = resources.displayMetrics.density
    private val inset = resources.getDimensionPixelSize(R.dimen.widget_playlist_content_inset)
    private val tileWidth = resources.getDimensionPixelSize(R.dimen.widget_tile_width)
    private val inflater = LayoutInflater.from(context)
    private val cellHeight = measuredHeight(inflater.inflate(
        if (tiled) R.layout.widget_playlist_tile else if (large) R.layout.widget_playlist_large_row else R.layout.widget_playlist_row, null))
    private val headingHeight = measuredHeight(inflater.inflate(R.layout.widget_playlist_list, null)
        .findViewById<View>(R.id.widget_playlist_heading).apply { visibility = View.VISIBLE })

    private fun measuredHeight(view: View): Int {
        view.measure(View.MeasureSpec.makeMeasureSpec(tileWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        return view.measuredHeight.coerceAtLeast(1)
    }

    fun grid(size: SizeF, heading: Boolean): PlaylistWidgetGrid {
        val width = (size.width * density).roundToInt() - 2 * inset
        val height = (size.height * density).roundToInt() - if (heading) headingHeight else 0
        // A single icon uses the full compact cell, even when two lines of text cannot fit.
        val iconSize = minOf(width, height)
        if (tiled && (width < tileWidth || height < cellHeight) && iconSize >= (24 * density).roundToInt()) {
            return PlaylistWidgetGrid(1, 1, iconSize = iconSize)
        }
        return PlaylistWidgetGrid.fit(width, height, tileWidth, cellHeight, tiled).copy(availableHeight = height, minRowHeight = cellHeight)
    }

    fun exact(options: Bundle): List<SizeF> = if (Build.VERSION.SDK_INT >= 31) {
        options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            .filter { it.width > 0 && it.height > 0 }.distinct().takeIf { it.size <= 16 }.orEmpty()
    } else emptyList()

    fun legacy(options: Bundle, landscape: Boolean): SizeF {
        val width = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        val height = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        return SizeF(options.getInt(width, if (tiled) 180 else 110).toFloat(),
            options.getInt(height, if (tiled) 110 else 84).toFloat())
    }

    // Match the exact-size candidate nearest the launcher's current orientation bounds.
    fun current(options: Bundle): SizeF {
        val target = legacy(options, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        return exact(options).minByOrNull {
            val dx = it.width - target.width
            val dy = it.height - target.height
            dx * dx + dy * dy
        } ?: target
    }
}
