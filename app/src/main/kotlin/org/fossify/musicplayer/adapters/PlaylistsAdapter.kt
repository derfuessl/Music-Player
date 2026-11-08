package org.fossify.musicplayer.adapters

import android.view.Menu
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.PlaylistCoverActivity
import org.fossify.musicplayer.helpers.PlaylistArtwork
import org.fossify.musicplayer.databinding.ItemPlaylistBinding
import org.fossify.musicplayer.dialogs.NewPlaylistDialog
import org.fossify.musicplayer.dialogs.RemovePlaylistDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.inlines.indexOfFirstOrNull
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Playlist
import org.greenrobot.eventbus.EventBus

class PlaylistsAdapter(
    activity: BaseSimpleActivity, items: ArrayList<Playlist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : BaseMusicAdapter<Playlist>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = items.getOrNull(position) ?: return
        holder.bindView(playlist, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, playlist)
        }
        bindViewHolder(holder)
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
            findItem(R.id.cab_playlist_cover).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_rename -> showRenameDialog()
            R.id.cab_select_all -> selectAll()
            R.id.cab_playlist_cover -> {
                getSelectedItems().singleOrNull()?.let { playlist ->
                    context.startActivity(Intent(context, PlaylistCoverActivity::class.java)
                        .putExtra(PlaylistCoverActivity.EXTRA_PLAYLIST_ID, playlist.id))
                    finishActMode()
                }
            }
        }
    }

    private fun askConfirmDelete() {
        RemovePlaylistDialog(context) { deleteFiles ->
            val playlists = getSelectedItems().toMutableList() as ArrayList<Playlist>
            val ids = playlists.map { it.id } as ArrayList<Int>
            if (deleteFiles) {
                ensureBackgroundThread {
                    val tracksToDelete = ids.flatMap { context.audioHelper.getPlaylistTracks(it) }
                    context.deleteTracks(tracksToDelete) {
                        removePlaylists(playlists)
                    }
                }
            } else {
                removePlaylists(playlists)
            }
        }
    }

    private fun removePlaylists(playlistsToDelete: ArrayList<Playlist>) {
        val ids = playlistsToDelete.mapTo(HashSet()) { it.id }
        ensureBackgroundThread {
            context.audioHelper.deletePlaylists(playlistsToDelete)
            context.runOnUiThread {
                // Positions may have changed while the database transaction ran.
                updateItems(items.filterNotTo(ArrayList()) { it.id in ids }, forceUpdate = true)
                EventBus.getDefault().post(Events.PlaylistsUpdated())
            }
        }
    }

    private fun showRenameDialog() {
        val selected = getSelectedItems().singleOrNull() ?: return
        NewPlaylistDialog(context, selected) {
            context.runOnUiThread {
                finishActMode()
                EventBus.getDefault().post(Events.PlaylistsUpdated())
            }
        }
    }

    private fun setupView(view: View, playlist: Playlist) {
        ItemPlaylistBinding.bind(view).apply {
            root.setupViewBackground(context)
            playlistFrame.isSelected = selectedKeys.contains(playlist.hashCode())
            playlistTitle.text = if (textToHighlight.isEmpty()) playlist.title else playlist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            playlistTitle.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, playlist.trackCount, playlist.trackCount)
            playlistTracks.text = tracks
            playlistTracks.setTextColor(textColor)
            val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_playlist_vector, textColor)
            PlaylistArtwork.displayPlaylist(playlistImage, playlist.id, placeholder)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        PlaylistArtwork.cancel(ItemPlaylistBinding.bind(holder.itemView).playlistImage)
        super.onViewRecycled(holder)
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.playlistSorting) ?: ""
}
