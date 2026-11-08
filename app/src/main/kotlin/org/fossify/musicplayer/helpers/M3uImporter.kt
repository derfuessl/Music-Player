package org.fossify.musicplayer.helpers

import android.net.Uri
import net.bjoernpetersen.m3u.M3uParser
import org.fossify.commons.extensions.showErrorToast
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Track
import java.io.File

class M3uImporter(
    val activity: SimpleActivity,
    val callback: (result: ImportResult) -> Unit
) {
    enum class ImportResult { IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL }

    fun importPlaylist(path: String, playListId: Int) {
        val result = try {
            // Open and close the original document on the import worker. Even an error
            // opening the stream must report completion to the UI.
            val content = path.startsWith("content://")
            val stream = when {
                content -> requireNotNull(activity.contentResolver.openInputStream(Uri.parse(path)))
                path.contains("/") -> File(path).inputStream()
                else -> activity.assets.open(path)
            }
            val entries = stream.reader().use { M3uParser.parse(it) }
            val index = M3uTrackIndex(activity.audioHelper.getPlaylistTracks(0))
            val directory = if (content) null else File(path).parentFile
            val matches = LinkedHashMap<Long, Track>()
            var missing = 0
            for (entry in entries) {
                val track = index.find(entry.location.toString(), entry.title, directory)
                if (track == null) missing++ else matches.putIfAbsent(track.mediaStoreId, track)
            }
            val saved = matches.isNotEmpty() && activity.audioHelper.copyToPlaylist(playListId, matches.values)
            when {
                !saved -> ImportResult.IMPORT_FAIL
                missing > 0 -> ImportResult.IMPORT_PARTIAL
                else -> ImportResult.IMPORT_OK
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
            ImportResult.IMPORT_FAIL
        }
        callback(result)
    }
}
