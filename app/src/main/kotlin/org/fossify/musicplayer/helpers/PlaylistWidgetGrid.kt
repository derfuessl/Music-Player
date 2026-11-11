package org.fossify.musicplayer.helpers

/** Dimensions use physical pixels after Android has applied density and the user's font size.
 * Count only complete entries. The configured selection survives any change of capacity. */
internal data class PlaylistWidgetGrid(
    val columns: Int,
    val capacity: Int,
    val availableHeight: Int = 0,
    val minRowHeight: Int = 0,
    val iconSize: Int = 0,
) {
    companion object {
        const val LIMIT = 12

        /** Cap the expansion so one playlist in a tall list still looks like a list row. */
        fun rowHeight(height: Int, minimum: Int, visible: Int): Int =
            if (visible == 0) 0 else (height / visible).coerceIn(minimum, minimum * 3 / 2)

        fun fit(width: Int, height: Int, cellWidth: Int, cellHeight: Int, tiled: Boolean): PlaylistWidgetGrid {
            val across = (width.coerceAtLeast(0) / cellWidth).coerceAtMost(if (tiled) LIMIT else 1)
            val down = (height.coerceAtLeast(0) / cellHeight).coerceAtMost(LIMIT)
            return PlaylistWidgetGrid(across, (across * down).coerceAtMost(LIMIT))
        }
    }
}
