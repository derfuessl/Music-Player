package org.fossify.musicplayer.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.databinding.DialogSelectPlaylistBinding
import org.fossify.musicplayer.databinding.ItemSelectPlaylistBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Playlist

class SelectPlaylistDialog(val activity: Activity, val callback: (playlistId: Int) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectPlaylistBinding::inflate)

    init {
        ensureBackgroundThread {
            val playlists = activity.audioHelper.getAllPlaylists()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                initDialog(playlists)
                if (playlists.isEmpty()) showNewPlaylistDialog()
            }
        }
        binding.dialogSelectPlaylistNewRadio.setOnClickListener {
            binding.dialogSelectPlaylistNewRadio.isChecked = false
            showNewPlaylistDialog()
        }
    }

    private fun initDialog(playlists: List<Playlist>) {
        // Keep the create action accessible while recycling the list of destinations.
        // Bind the click listener again on reuse so it always refers to the visible ID.
        binding.dialogSelectPlaylistLinear.adapter = object : BaseAdapter() {
            override fun getCount() = playlists.size
            override fun getItem(position: Int) = playlists[position]
            override fun getItemId(position: Int) = playlists[position].id.toLong()
            override fun hasStableIds() = true
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView?.let(ItemSelectPlaylistBinding::bind)
                    ?: ItemSelectPlaylistBinding.inflate(activity.layoutInflater, parent, false)
                val playlist = getItem(position)
                row.selectPlaylistItemRadioButton.apply {
                    text = playlist.title
                    isChecked = false
                    setOnClickListener {
                        dialog?.dismiss()
                        callback(playlist.id)
                    }
                }
                return row.root
            }
        }
        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this) { dialog = it }
        }
    }

    private fun showNewPlaylistDialog() {
        NewPlaylistDialog(activity) {
            dialog?.dismiss()
            callback(it)
        }
    }
}
