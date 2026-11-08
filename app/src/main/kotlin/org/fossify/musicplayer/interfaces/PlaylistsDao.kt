package org.fossify.musicplayer.interfaces

import androidx.room.*
import org.fossify.musicplayer.models.Playlist

data class PlaylistSummary(@Embedded val playlist: Playlist, val songCount: Int)

@Dao
interface PlaylistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(playlist: Playlist): Long

    @Delete
    fun deletePlaylists(playlists: List<Playlist>)

    // One snapshot includes empty playlists and counts membership rows, not source files.
    @Query("SELECT p.*, COUNT(t.id) AS songCount FROM playlists p LEFT JOIN tracks t ON t.playlist_id = p.id GROUP BY p.id")
    fun summaries(): List<PlaylistSummary>

    @Query("SELECT * FROM playlists")
    fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE title = :title COLLATE NOCASE")
    fun getPlaylistWithTitle(title: String): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistWithId(id: Int): Playlist?

    @Update
    fun update(playlist: Playlist)
}
