package org.fossify.musicplayer.helpers

/** Geometry is independent of Android so the fallback layouts can be tested without a device. */
internal object CoverMosaic {
    data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)

    // Fewer than four covers fill the square without duplicating images or leaving blank tiles.
    fun cells(count: Int): List<Cell> = when (count.coerceAtMost(4)) {
        0 -> emptyList()
        1 -> listOf(Cell(0f, 0f, 1f, 1f))
        2 -> listOf(Cell(0f, 0f, .5f, 1f), Cell(.5f, 0f, 1f, 1f))
        3 -> listOf(Cell(0f, 0f, .5f, 1f), Cell(.5f, 0f, 1f, .5f), Cell(.5f, .5f, 1f, 1f))
        else -> listOf(Cell(0f, 0f, .5f, .5f), Cell(.5f, 0f, 1f, .5f), Cell(0f, .5f, .5f, 1f), Cell(.5f, .5f, 1f, 1f))
    }

    /** Lazy traversal stops extracting artwork as soon as four distinct images have been found. */
    fun <T, K> firstDistinct(items: Sequence<T>, key: (T) -> K): List<T> = items.distinctBy(key).take(4).toList()
}
