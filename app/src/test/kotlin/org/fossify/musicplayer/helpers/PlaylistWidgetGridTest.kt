package org.fossify.musicplayer.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistWidgetGridTest {
    @Test fun sixPlusSixAndTwelveHaveTheSameRowPitchOnTablet() {
        // Some hosts subtract their default padding before reporting the content width.
        for (width in listOf(88, 264, 280, 360, 440, 540, 820, 1280)) {
            for (tiled in listOf(false, true)) {
                val columns = PlaylistWidgetGrid.columns(tiled, width)
                assertEquals(0, 6 % columns)
                val halfRows = (6 + columns - 1) / columns
                val fullRows = (12 + columns - 1) / columns
                assertEquals(2 * halfRows, fullRows)
            }
        }
    }

    @Test fun smallPanelsAndWideLayoutsHaveBoundedColumnCounts() {
        assertEquals(12, PlaylistWidgetGrid.LIMIT)
        assertEquals(1, PlaylistWidgetGrid.columns(true, 88))
        assertEquals(1, PlaylistWidgetGrid.columns(false, 0))
        assertEquals(2, PlaylistWidgetGrid.columns(false, 420))
        assertEquals(6, PlaylistWidgetGrid.columns(true, 1600))
    }
}
