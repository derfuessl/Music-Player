package org.fossify.musicplayer.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import org.fossify.musicplayer.helpers.PATH

/**
 * Enum class representing custom commands that are used within the app and by media controller clients (e.g. system media controls).
 */
enum class CustomCommands(val customAction: String) {
    CLOSE_PLAYER(customAction = PATH + "CLOSE_PLAYER"),
    RELOAD_CONTENT(customAction = PATH + "RELOAD_CONTENT"),
    TOGGLE_SLEEP_TIMER(customAction = PATH + "TOGGLE_SLEEP_TIMER"),
    SET_NEXT_ITEM(customAction = PATH + "SET_NEXT_ITEM"),
    SET_SHUFFLE_ORDER(customAction = PATH + "SET_SHUFFLE_ORDER"),
    START_STOP_DANCE(customAction = PATH + "START_STOP_DANCE"),
    DANCE_PREVIOUS(PATH + "DANCE_PREVIOUS"),
    DANCE_NEXT(PATH + "DANCE_NEXT"),
    STOP_STOP_DANCE(customAction = PATH + "STOP_STOP_DANCE"),
    GET_STOP_DANCE_STATE(customAction = PATH + "GET_STOP_DANCE_STATE");

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        const val MIN_PLAY_SECONDS = "min_play_seconds"
        const val MAX_PLAY_SECONDS = "max_play_seconds"
        const val PAUSE_SECONDS = "pause_seconds"
        const val TRACK_TITLE = "dance_track_title"
        const val HAS_PREVIOUS = "dance_has_previous"
        const val HAS_NEXT = "dance_has_next"
        const val STOP_DANCE_PHASE = "stop_dance_phase"
        const val STOP_DANCE_CAN_START = "stop_dance_can_start"
        const val STOP_DANCE_IS_PLAYING = "stop_dance_is_playing"

        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return values().find { it.customAction == sessionCommand.customAction }
        }
    }
}

internal val customCommands = CustomCommands.values().map { it.sessionCommand }

internal fun Context.getCustomLayout(): List<CommandButton> {
    return listOf(
        CommandButton.Builder()
            .setDisplayName(getString(org.fossify.commons.R.string.close))
            .setSessionCommand(CustomCommands.CLOSE_PLAYER.sessionCommand)
            .setIconResId(org.fossify.commons.R.drawable.ic_cross_vector)
            .build()
    )
}
