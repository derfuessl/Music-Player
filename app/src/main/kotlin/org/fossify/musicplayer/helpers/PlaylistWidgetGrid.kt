package org.fossify.musicplayer.helpers

/** Two panels with the same width must choose the same column boundaries. In particular,
 * rounding each half-height to whole rows would sometimes give a stacked pair a different
 * column count than its full-height equivalent. The launcher measures row heights at layout. */
internal object PlaylistWidgetGrid {
    const val LIMIT = 12

    fun columns(tiled: Boolean, width: Int): Int {
        val usableWidth = (width - 12).coerceAtLeast(1) // Two 6dp horizontal panel insets.
        val columnWidth = if (tiled) 80 else 200
        // A twelve-item panel and two six-item panels need identical complete rows.
        // Four or five columns would introduce a partly empty row at the join.
        val fittingColumns = usableWidth / columnWidth
        return when {
            fittingColumns >= 6 -> 6
            fittingColumns >= 3 -> 3
            fittingColumns >= 2 -> 2
            else -> 1
        }
    }
}
