package org.fossify.musicplayer.helpers

import org.fossify.musicplayer.models.Playlist
import org.junit.Assert.*
import org.junit.Test

class WidgetPlaylistIndexTest {
    @Test fun searchesFiftyThousandRowsWithoutLosingIdsOrTreatingInputAsSql() {
        val rows = (1..50_000).map { Playlist(it, if (it % 10_000 == 0) "MÄUSE 100%_Mix" else "Musik $it") }
        val index = WidgetPlaylistIndex(rows)
        assertEquals(listOf("10000","20000","30000","40000","50000"), index.search(" mäuse ").map { it.key })
        assertEquals(5, index.search("%_").size)
        assertEquals(emptyList<WidgetPlaylistIndex.Row>(), index.search("kein Treffer"))
        assertSame(index.rows, index.search("  "))
    }
}
