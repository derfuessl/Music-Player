package org.fossify.musicplayer.interfaces

import androidx.room.*
import org.fossify.musicplayer.models.Playlist

@Dao
interface PlaylistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(playlist: Playlist): Long

    @Delete
    fun deletePlaylists(playlists: List<Playlist>)

    @Query("SELECT * FROM playlists")
    fun getAll(): List<Playlist>

    // Use the same ordering in the picker and on the launcher. A widget refresh needs at
    // most twelve objects even when the library contains thousands of playlists.
    @Query("SELECT * FROM playlists ORDER BY title COLLATE NOCASE, id LIMIT :limit")
    fun widgetPlaylists(limit: Int): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id IN (:ids) ORDER BY title COLLATE NOCASE, id LIMIT :limit")
    fun widgetPlaylistsById(ids: List<Int>, limit: Int): List<Playlist>

    @Query("SELECT * FROM playlists WHERE title = :title COLLATE NOCASE")
    fun getPlaylistWithTitle(title: String): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistWithId(id: Int): Playlist?

    @Update
    fun update(playlist: Playlist)
}
