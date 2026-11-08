package org.fossify.musicplayer.helpers

import java.io.File
import org.fossify.musicplayer.models.Track
import org.junit.Assert.*
import org.junit.Test

class M3uTrackIndexTest {
    private fun song(id: Long, name: String, path: String) = Track(
        id, id, name, "", path, 0, "", "", "", 0, null, null, "", 0, 0, 0, 0, 0, 0
    )

    @Test fun exactFileWinsAndAmbiguousTitlesAreSkipped() {
        val a = song(1, "Dance", "/music/a.mp3")
        val b = song(2, "Dance", "/music/b.mp3")
        val index = M3uTrackIndex(listOf(a, b))
        assertSame(b, index.find(b.path, "Dance", null))
        assertNull(index.find("missing.mp3", "Dance", null))
        assertNull(index.find("missing.mp3", null, null))
    }

    @Test fun resolvesRelativeFilesFileUrisAndUniqueTitles() {
        val directory = File("library").absoluteFile
        val track = song(1, "Song", File(directory, "with spaces.mp3").path)
        val index = M3uTrackIndex(listOf(track))
        assertSame(track, index.find("./with spaces.mp3", null, directory))
        assertSame(track, index.find(File(track.path).toURI().toString(), null, null))
        assertSame(track, index.find("moved.mp3", "Song", null))
    }
}
