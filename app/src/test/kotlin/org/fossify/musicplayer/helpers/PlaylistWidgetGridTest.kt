package org.fossify.musicplayer.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistWidgetGridTest {
    private fun fit(w: Int, h: Int, tiled: Boolean = false, row: Int = if (tiled) 84 else 42) =
        PlaylistWidgetGrid.fit(w, h, 76, row, tiled).capacity

    @Test fun listUsesOneColumnAndTwoEntriesPerStandardCell() {
        assertEquals(2, fit(380, 84))
        assertEquals(6, fit(380, 252))
        assertEquals(12, fit(380, 504))
    }
    @Test fun tilesUseBothDimensionsAndKeepAllFourColumns() {
        assertEquals(1, fit(76, 84, true))
        assertEquals(6, fit(228, 168, true))
        assertEquals(12, fit(304, 252, true))
    }
    @Test fun neverCountPartialCellsOrMoreThanTwelve() {
        assertEquals(0, fit(75, 168, true))
        assertEquals(0, fit(76, 83, true))
        assertEquals(1, fit(152, 83))
        assertEquals(0, fit(80, -1))
        assertEquals(12, fit(5000, 5000, true))
    }
    @Test fun headingAndLargerSystemFontsReduceCapacity() {
        assertEquals(5, fit(152, 252 - 20))
        assertEquals(4, fit(152, 252, row = 55))
    }
}
