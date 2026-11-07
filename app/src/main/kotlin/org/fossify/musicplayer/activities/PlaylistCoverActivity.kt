package org.fossify.musicplayer.activities

import android.content.res.ColorStateList
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ActivityPlaylistCoverBinding
import org.fossify.musicplayer.databinding.ItemCoverTrackBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.helpers.PlaylistArtwork
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.Executors

/**
 * Edits only a playlist's copied artwork, never the source music or playlist membership. Choices
 * are saved immediately. The system picker grants access to the selected file only; no photo
 * library permission is required. Private storage makes that temporary URI grant sufficient.
 */
class PlaylistCoverActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityPlaylistCoverBinding::inflate)
    private val worker = Executors.newSingleThreadExecutor()
    private var playlistId = 0
    private var tracks: List<Track> = emptyList()
    private var loaded = false
    private var busy = true
    private var pendingImage: Uri? = null
    private var trackDialog: AlertDialog? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingImage = uri
            applyPendingImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        playlistId = intent.getIntExtra(EXTRA_PLAYLIST_ID, 0)
        pendingImage = savedInstanceState?.getString("pending_image")?.let(Uri::parse)
        if (playlistId <= 0) {
            finish()
            return
        }
        setupEdgeToEdge(padBottomSystem = listOf(binding.coverScroll))
        setupMaterialScrollListener(binding.coverScroll, binding.coverAppbar)
        binding.coverContent.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            // Keep the same comfortable column width as Stop Dance on large tablets.
            val margin = (24 * resources.displayMetrics.density).toInt()
            val inset = maxOf(margin, (right - left - (560 * resources.displayMetrics.density).toInt()) / 2)
            if (view.paddingLeft != inset) view.setPadding(inset, margin, inset, margin)
        }
        binding.coverFromTracks.setOnClickListener { chooseTrack() }
        binding.coverFromImage.setOnClickListener {
            try { pickImage.launch("image/*") } catch (e: Exception) { toast(R.string.playlist_cover_failed) }
        }
        binding.coverAutomatic.setOnClickListener { editCover { PlaylistArtwork.reset(applicationContext, playlistId) } }
        updateButtons()
        worker.execute {
            val playlist = playlistDAO.getPlaylistWithId(playlistId)
            val playlistTracks = if (playlist != null) audioHelper.getPlaylistTracks(playlistId) else emptyList()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (playlist == null) {
                        toast(R.string.playlist_cover_missing)
                        finish()
                    } else {
                        loaded = true
                        tracks = playlistTracks
                        binding.coverPlaylistTitle.text = playlist.title
                        refreshPreview()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.coverAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.coverContent)
        val primary = getProperPrimaryColor()
        listOf(binding.coverFromTracks, binding.coverFromImage).forEach {
            it.backgroundTintList = ColorStateList.valueOf(primary)
            it.setTextColor(primary.getContrastColor())
        }
        binding.coverAutomatic.setTextColor(primary)
        binding.coverAutomatic.strokeColor = ColorStateList.valueOf(primary)
        binding.coverProgress.setIndicatorColor(primary)
    }

    private fun refreshPreview() {
        busy = true
        updateButtons()
        val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_playlist_vector, getProperTextColor())
        binding.coverPreview.setImageDrawable(placeholder)
        worker.execute {
            val bitmap = try { PlaylistArtwork.loadPlaylist(applicationContext, playlistId) } catch (e: Exception) { null }
            val custom = PlaylistArtwork.hasCustom(applicationContext, playlistId)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (bitmap != null) binding.coverPreview.setImageBitmap(bitmap)
                    binding.coverMode.setText(if (custom) R.string.playlist_cover_custom else R.string.playlist_cover_auto_mode)
                    busy = false
                    updateButtons()
                    applyPendingImage()
                }
            }
        }
    }

    private fun editCover(action: () -> Unit) {
        if (busy || !loaded) return
        busy = true
        updateButtons()
        worker.execute {
            val succeeded = try {
                action()
                true
            } catch (e: Exception) {
                false
            }
            if (succeeded) EventBus.getDefault().post(Events.PlaylistsUpdated())
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    toast(if (succeeded) R.string.playlist_cover_saved else R.string.playlist_cover_failed)
                    refreshPreview()
                }
            }
        }
    }

    private fun applyPendingImage() {
        // A picker result can arrive after rotation, before the playlist query has completed.
        // Keep it until initialization finishes instead of silently discarding the user's choice.
        if (busy || !loaded) return
        val uri = pendingImage ?: return
        pendingImage = null
        editCover {
            val image = PlaylistArtwork.loadImage(applicationContext, uri) ?: error("Unreadable image")
            PlaylistArtwork.save(applicationContext, playlistId, image)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_image", pendingImage?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun chooseTrack() {
        if (tracks.isEmpty()) {
            toast(R.string.playlist_cover_empty)
            return
        }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlaylistCoverActivity)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(resources.displayMetrics.heightPixels / 2, (480 * resources.displayMetrics.density).toInt()))
        }
        val holder = FrameLayout(this).apply { addView(list) }
        val dialog = getAlertDialogBuilder().setTitle(R.string.playlist_cover_from_tracks)
            .setView(holder).setNegativeButton(org.fossify.commons.R.string.cancel, null).create()
        trackDialog = dialog
        list.adapter = CoverTracksAdapter(tracks) { track ->
            dialog.dismiss()
            busy = true
            updateButtons()
            worker.execute {
                val bitmap = PlaylistArtwork.loadTrack(applicationContext, track)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        busy = false
                        updateButtons()
                        if (bitmap == null) toast(R.string.playlist_cover_no_image)
                        else editCover { PlaylistArtwork.save(applicationContext, playlistId, bitmap) }
                    }
                }
            }
        }
        // Detach the adapter on dismiss so RecyclerView cancels pending thumbnail requests.
        dialog.setOnDismissListener { list.adapter = null; trackDialog = null }
        dialog.show()
    }

    private fun updateButtons() {
        binding.coverProgress.visibility = if (busy) View.VISIBLE else View.GONE
        listOf(binding.coverFromTracks, binding.coverFromImage, binding.coverAutomatic).forEach {
            it.isEnabled = loaded && !busy
        }
    }

    override fun onDestroy() {
        trackDialog?.dismiss()
        worker.shutdownNow()
        super.onDestroy()
    }

    private inner class CoverTracksAdapter(private val items: List<Track>, private val choose: (Track) -> Unit) :
        RecyclerView.Adapter<CoverTracksAdapter.Holder>() {
        inner class Holder(val row: ItemCoverTrackBinding) : RecyclerView.ViewHolder(row.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemCoverTrackBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val track = items[position]
            holder.row.apply {
                coverTrackTitle.text = track.title
                coverTrackArtist.text = track.artist
                coverTrackTitle.setTextColor(getProperTextColor())
                coverTrackArtist.setTextColor(getProperTextColor())
                val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_music_note_vector, getProperTextColor())
                PlaylistArtwork.displayTrack(coverTrackImage, track, placeholder)
                root.setOnClickListener { choose(track) }
            }
        }

        override fun onViewRecycled(holder: Holder) {
            PlaylistArtwork.cancel(holder.row.coverTrackImage)
            super.onViewRecycled(holder)
        }
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_cover_id"
    }
}
