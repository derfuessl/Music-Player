package org.fossify.musicplayer.helpers

import org.fossify.musicplayer.models.Playlist

/** Snapshot for one editing session, built on a worker. Normalize each title once so typing
 * never sorts the library, opens music files or repeatedly converts every title to lowercase.
 * A blank search reuses the original rows; all results preserve the database ordering. */
internal class WidgetPlaylistIndex(playlists: List<Playlist>) {
    data class Row(val key: String, val title: String) {
        val searchTitle = title.lowercase()
    }
    val rows = playlists.map { Row(it.id.toString(), it.title) }
    val keys = rows.mapTo(HashSet()) { it.key }

    fun search(text: String): List<Row> {
        val query = text.trim().lowercase()
        return if (query.isEmpty()) rows else rows.filter { query in it.searchTitle }
    }
}
