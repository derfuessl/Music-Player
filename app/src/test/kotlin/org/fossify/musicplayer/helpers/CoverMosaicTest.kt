package org.fossify.musicplayer.helpers

import org.junit.Assert.*
import org.junit.Test

class CoverMosaicTest {
    @Test fun emptyPlaylistHasNoTiles() {
        assertTrue(CoverMosaic.cells(0).isEmpty())
    }

    @Test fun oneImageUsesWholeSquare() {
        assertEquals(listOf(CoverMosaic.Cell(0f, 0f, 1f, 1f)), CoverMosaic.cells(1))
    }

    @Test fun layoutsCoverTheSquareExactlyWithoutOverlap() {
        for (count in 1..4) {
            val cells = CoverMosaic.cells(count)
            assertEquals(count, cells.size)
            assertEquals(1f, cells.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }.toFloat(), 0f)
            cells.forEach { assertTrue(it.left >= 0 && it.top >= 0 && it.right <= 1 && it.bottom <= 1) }
            cells.forEachIndexed { i, a -> cells.drop(i + 1).forEach { b ->
                val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue(width <= 0 || height <= 0)
            } }
        }
    }

    @Test fun fourImagesAreARegularGrid() {
        CoverMosaic.cells(4).forEach {
            assertEquals(.5f, it.right - it.left, 0f)
            assertEquals(.5f, it.bottom - it.top, 0f)
        }
    }

    @Test fun duplicateAlbumCoversOccupyOnlyOneTile() {
        assertEquals(listOf("A", "B", "C"), CoverMosaic.firstDistinct(sequenceOf("A", "A", "B", "A", "C")) { it })
    }

    @Test fun extractionStopsAfterFourDistinctImages() {
        var reads = 0
        val source = sequence { for (i in 0..100) { reads++; yield(i) } }
        assertEquals(listOf(0, 1, 2, 3), CoverMosaic.firstDistinct(source) { it })
        assertEquals(4, reads)
    }
}
