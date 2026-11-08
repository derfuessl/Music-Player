package org.fossify.musicplayer.helpers

import org.fossify.musicplayer.models.Track
import java.io.File
import java.net.URI

/** Build once per import. The exact file wins even if another recording has the same
 * title. Title-only fallback is safe only when it identifies a single recording. */
internal class M3uTrackIndex(tracks: List<Track>) {
    private val byPath = tracks.associateBy { it.path }
    private val byTitle = HashMap<String, Track?>()

    init {
        for (track in tracks) {
            val previous = byTitle[track.title]
            if (!byTitle.containsKey(track.title)) byTitle[track.title] = track
            else if (previous?.path != track.path) byTitle[track.title] = null
        }
    }

    fun find(location: String, title: String?, directory: File?): Track? {
        byPath[location]?.let { return it }
        val path = if (location.startsWith("file:", ignoreCase = true)) {
            runCatching { File(URI(location)).path }.getOrNull()
        } else if (directory != null && !File(location).isAbsolute) {
            File(directory, location).normalize().path
        } else null
        return byPath[path] ?: byTitle[title]
    }
}
