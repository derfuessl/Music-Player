package org.fossify.musicplayer.playback

import android.os.Bundle
import org.fossify.musicplayer.extensions.playlistDAO
import org.fossify.musicplayer.helpers.PLAYLIST
import org.fossify.musicplayer.helpers.PlaylistArtwork
import android.os.ConditionVariable
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.fossify.musicplayer.extensions.addRemainingMediaItems
import org.fossify.musicplayer.helpers.EXTRA_NEXT_MEDIA_ID
import org.fossify.musicplayer.helpers.EXTRA_SHUFFLE_INDICES
import org.fossify.musicplayer.playback.player.updatePlaybackState
import java.util.concurrent.Executors

@UnstableApi
internal fun PlaybackService.getMediaSessionCallback() = object : MediaLibrarySession.Callback {
    private val browsers = mutableMapOf<MediaSession.ControllerInfo, String>()
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    private fun <T> callWhenSourceReady(action: () -> T): ListenableFuture<T> {
        val conditionVariable = ConditionVariable()
        return if (mediaItemProvider.whenReady { conditionVariable.open() }) {
            executorService.submit<T> {
                action()
            }
        } else {
            executorService.submit<T> {
                conditionVariable.block()
                action()
            }
        }
    }

    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.ConnectionResult> {
        val resultBuilder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
        if (controller.isTrusted) {
            val availableSessionCommands = DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            for (command in customCommands) {
                availableSessionCommands.add(command)
            }
            resultBuilder.setAvailableSessionCommands(availableSessionCommands.build())
        }

        return Futures.immediateFuture(resultBuilder.build())
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        val customLayout = getCustomLayout()
        if (customLayout.isNotEmpty() && controller.controllerVersion != 0) {
            mediaSession.setCustomLayout(controller, customLayout)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val command = CustomCommands.fromSessionCommand(customCommand)
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))

        when (command) {
            CustomCommands.DANCE_PREVIOUS, CustomCommands.DANCE_NEXT, CustomCommands.GET_STOP_DANCE_STATE -> {
                val result = SettableFuture.create<SessionResult>()
                withPlayer {
                    if (command != CustomCommands.GET_STOP_DANCE_STATE) stopDance.changeTrack(command == CustomCommands.DANCE_NEXT)
                    result.set(SessionResult(SessionResult.RESULT_SUCCESS, stopDance.stateExtras()))
                }
                return result
            }
            CustomCommands.CLOSE_PLAYER -> stopService()
            CustomCommands.RELOAD_CONTENT -> reloadContent()
            CustomCommands.TOGGLE_SLEEP_TIMER -> toggleSleepTimer()
            CustomCommands.SET_SHUFFLE_ORDER -> setShuffleOrder(args)
            CustomCommands.SET_NEXT_ITEM -> setNextItem(args)
            CustomCommands.START_STOP_DANCE, CustomCommands.STOP_STOP_DANCE -> {
                val result = SettableFuture.create<SessionResult>()
                // Copy IPC input before dispatch. Invalid/missing values never change an active run.
                val settings = if (command == CustomCommands.START_STOP_DANCE) {
                    try {
                        StopDanceController.Settings(
                            args.getInt(CustomCommands.MIN_PLAY_SECONDS),
                            args.getInt(CustomCommands.MAX_PLAY_SECONDS),
                            args.getInt(CustomCommands.PAUSE_SECONDS)
                        )
                    } catch (e: IllegalArgumentException) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    }
                } else null
                withPlayer {
                    val code = if (settings == null) {
                        stopDance.controller.stop()
                        SessionResult.RESULT_SUCCESS
                    } else if (stopDance.controller.start(settings)) {
                        SessionResult.RESULT_SUCCESS
                    } else {
                        SessionError.ERROR_INVALID_STATE
                    }
                    result.set(SessionResult(code, stopDance.stateExtras()))
                }
                return result
            }
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (params != null && params.isRecent) {
            // The service currently does not support recent playback. Tell System UI by returning
            // an error of type 'RESULT_ERROR_NOT_SUPPORTED' for a `params.isRecent` request. See
            // https://github.com/androidx/media/issues/355
            return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        }

        return Futures.immediateFuture(
            LibraryResult.ofItem(
                mediaItemProvider.getRootItem(),
                params
            )
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        currentRoot = parentId
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)

        LibraryResult.ofItemList(children, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ) = callWhenSourceReady {
        val item = mediaItemProvider[mediaId]
            ?: return@callWhenSourceReady LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)

        LibraryResult.ofItem(item, null)
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)

        browsers[browser] = parentId
        session.notifyChildrenChanged(browser, parentId, children.size, params)
        LibraryResult.ofVoid()
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val settableFuture = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        executorService.execute {
            var futureSet = false
            mediaItemProvider.getRecentItemsLazily {
                // resume playback as quickly as possible: https://github.com/androidx/media/issues/111
                if (!futureSet) {
                    settableFuture.set(it)
                    futureSet = true
                } else {
                    player.addRemainingMediaItems(it.mediaItems, it.startIndex)
                }
            }
        }

        return settableFuture
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (mediaItems.isEmpty() || controller.packageName == packageName) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        // this is to avoid single items in the queue: https://github.com/androidx/media/issues/156
        var queueItems = mediaItems
        val startItemId = mediaItems[0].mediaId
        val currentItems = mediaItemProvider.getChildren(currentRoot).orEmpty()

        queueItems = if (currentItems.any { it.mediaId == startItemId }) {
            currentItems.toMutableList()
        } else {
            mediaItemProvider.getDefaultQueue()?.toMutableList() ?: queueItems
        }

        val startItemIndex = queueItems.indexOfFirst { it.mediaId == startItemId }
        return super.onSetMediaItems(mediaSession, controller, queueItems, startItemIndex, startPositionMs)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return executorService.submit<List<MediaItem>> {
            // Resolve playlist context on the existing library worker. Each queue owns its
            // metadata, so switching to an album or a folder clears it automatically.
            val playlistId = mediaItems.firstOrNull()?.requestMetadata?.extras?.getInt(PLAYLIST, -1)
            val playlist = if (controller.packageName == packageName && playlistId != null && playlistId > 0) {
                playlistDAO.getPlaylistWithId(playlistId)
            } else null
            val artwork = playlist?.let { PlaylistArtwork.notificationUri(this@getMediaSessionCallback, it.id) }
            val items = mediaItems.mapNotNull { mediaItem ->
                val resolved = if (mediaItem.requestMetadata.searchQuery != null) {
                    getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!) ?: return@mapNotNull null
                } else {
                    mediaItemProvider[mediaItem.mediaId] ?: mediaItem
                }
                if (playlist == null) resolved else {
                    // SystemUI versions use either artist or display subtitle. Preserve the
                    // track title, artist credit and original Track record used by the app.
                    val metadata = resolved.mediaMetadata
                    val subtitle = listOf(playlist.title, metadata.artist?.toString())
                        .filterNot { it.isNullOrBlank() }.distinct().joinToString(" · ")
                    resolved.buildUpon().setMediaMetadata(metadata.buildUpon()
                        .setSubtitle(subtitle).setArtist(subtitle)
                        .apply { if (artwork != null) setArtworkUri(artwork) }
                        .build()).build()
                }
            }
            items
        }
    }

    private fun getMediaItemFromSearchQuery(query: String): MediaItem? {
        return mediaItemProvider.getItemFromSearch(query.lowercase()) ?: mediaItemProvider.getRandomItem()
    }

    private fun reloadContent() {
        mediaItemProvider.reload()
        mediaItemProvider.whenReady {
            val rootItem = mediaItemProvider.getRootItem()
            val rootItemCount = mediaItemProvider.getChildren(rootItem.mediaId)?.size ?: 0

            executorService.execute {
                browsers.forEach { (browser, parentId) ->
                    val itemCount = mediaItemProvider.getChildren(parentId)?.size ?: 0
                    mediaSession.notifyChildrenChanged(browser, parentId, itemCount, null)
                    mediaSession.notifyChildrenChanged(browser, rootItem.mediaId, rootItemCount, null)
                }
            }
        }
    }

    private fun setShuffleOrder(args: Bundle) {
        val indices = args.getIntArray(EXTRA_SHUFFLE_INDICES) ?: return
        withPlayer {
            setShuffleIndices(indices)
        }
    }

    private fun setNextItem(args: Bundle) {
        val mediaId = args.getString(EXTRA_NEXT_MEDIA_ID) ?: return
        callWhenSourceReady {
            val mediaItem = mediaItemProvider[mediaId] ?: return@callWhenSourceReady
            withPlayer {
                setNextMediaItem(mediaItem)
                updatePlaybackState()
            }
        }
    }
}
