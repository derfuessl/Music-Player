package org.fossify.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.DialogNewPlaylistBinding
import org.fossify.musicplayer.extensions.getTracksDB
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.models.Playlist
import java.util.concurrent.Callable

class NewPlaylistDialog(val activity: Activity, val playlist: Playlist? = null, val callback: (playlistId: Int) -> Unit) {
    private val binding by activity.viewBinding(DialogNewPlaylistBinding::inflate)

    init {
        binding.newPlaylistTitle.setText(playlist?.title.orEmpty())
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                val dialogTitle = if (playlist == null) R.string.create_new_playlist else R.string.rename_playlist
                activity.setupDialogStuff(binding.root, this, dialogTitle) { dialog ->
                    dialog.showKeyboard(binding.newPlaylistTitle)
                    val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    save.setOnClickListener {
                        val title = binding.newPlaylistTitle.value
                        if (title.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.empty_name)
                            return@setOnClickListener
                        }
                        save.isEnabled = false
                        ensureBackgroundThread {
                            // Check the name and write it in one transaction. A rename must
                            // not resurrect a playlist deleted while its dialog was open.
                            val result = runCatching {
                                activity.getTracksDB().runInTransaction(Callable {
                                    val dao = activity.playlistDAO
                                    val existing = dao.getPlaylistWithTitle(title)
                                    when {
                                        existing != null && existing.id != playlist?.id -> -1
                                        playlist == null -> dao.insert(Playlist(0, title)).toInt()
                                        dao.getPlaylistWithId(playlist.id) == null -> 0
                                        else -> {
                                            dao.update(playlist.copy(title = title))
                                            playlist.id
                                        }
                                    }
                                })
                            }
                            activity.runOnUiThread {
                                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                                save.isEnabled = true
                                val id = result.getOrDefault(0)
                                when {
                                    id > 0 -> {
                                        dialog.dismiss()
                                        callback(id)
                                    }
                                    id == -1 -> activity.toast(R.string.playlist_name_exists)
                                    else -> activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                            }
                        }
                    }
                }
            }
    }
}
