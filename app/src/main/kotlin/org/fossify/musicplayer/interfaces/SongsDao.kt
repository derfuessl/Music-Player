package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.Track

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    // Adding an existing membership again preserves its ID and manual position.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMemberships(tracks: List<Track>)

    @Query("SELECT COALESCE(MAX(order_in_playlist), -1) FROM tracks WHERE playlist_id = :id")
    fun lastPlaylistPosition(id: Int): Int

    @Query("SELECT * FROM tracks WHERE playlist_id = 0")
    fun getAll(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksFromPlaylist(playlistId: Int): List<Track>

    @Query("SELECT * FROM tracks WHERE artist_id = :artistId")
    fun getTracksFromArtist(artistId: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE album_id = :albumId")
    fun getTracksFromAlbum(albumId: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id IN (:ids)")
    fun getPlaylistMemberships(ids: List<Int>): List<Track>

    @Query("SELECT * FROM tracks WHERE folder_name = :folderName COLLATE NOCASE GROUP BY media_store_id")
    fun getTracksFromFolder(folderName: String): List<Track>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId")
    fun getTrackWithMediaStoreId(mediaStoreId: Long): Track?

    @Query("SELECT * FROM tracks WHERE genre_id = :genreId")
    fun getGenreTracks(genreId: Long): List<Track>

    @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId")
    fun removeTrack(mediaStoreId: Long)

    @Query("SELECT * FROM tracks WHERE playlist_id > 0 AND NOT EXISTS (SELECT 1 FROM tracks AS source WHERE source.playlist_id = 0 AND source.media_store_id = tracks.media_store_id AND source.path = tracks.path)")
    fun unmatchedMemberships(): List<Track>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    fun removeRows(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId AND media_store_id IN (:mediaStoreIds)")
    fun removeTracksFromPlaylist(playlistId: Int, mediaStoreIds: List<Long>)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)

    @Query("UPDATE tracks SET order_in_playlist = :index WHERE id = :id")
    fun updateOrderInPlaylist(index: Int, id: Long)
}
