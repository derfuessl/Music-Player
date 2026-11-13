package org.fossify.musicplayer.fragments

import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.FragmentStopDanceBinding
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.viewBinding
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.playback.PlaybackService
import org.fossify.musicplayer.playback.StopDanceController

/**
 * A remote control for the service, never a second dance timer. The state query makes the screen
 * correct after rotation, external pause/focus loss, or returning from another screen. All callbacks
 * and view mutations run on the main looper; the service serializes commands on its player looper.
 */
@UnstableApi
class StopDanceFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private val binding by viewBinding(FragmentStopDanceBinding::bind)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiExecutor = ContextCompat.getMainExecutor(context)
    private var connection: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var requestVersion = 0L
    private var commandPending = false
    private var active = false
    private var canStart = false

    private val poll = Runnable { queryState() }
    private val timeout = Runnable { showUnavailable() }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Restore only saved input preferences, never a saved "running" flag. The service is
        // authoritative and must be queried before Start/Stop become available.
        binding.stopDanceMinInput.setText(context.config.stopDanceMinSeconds.toString())
        binding.stopDanceMaxInput.setText(context.config.stopDanceMaxSeconds.toString())
        binding.stopDancePauseInput.setText(context.config.stopDancePauseSeconds.toString())
        binding.stopDancePrevious.setOnClickListener { sendCommand(CustomCommands.DANCE_PREVIOUS) }
        binding.stopDanceNext.setOnClickListener { sendCommand(CustomCommands.DANCE_NEXT) }
        binding.stopDanceStart.setOnClickListener { startDance() }
        binding.stopDanceStop.setOnClickListener { sendCommand(CustomCommands.STOP_STOP_DANCE) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Keep readable controls centered on tablets while allowing narrow phones and large text.
        val density = resources.displayMetrics.density
        val inset = maxOf((24 * density).toInt(), (w - (560 * density).toInt()) / 2)
        binding.stopDanceContent.setPadding(inset, (24 * density).toInt(), inset, (24 * density).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        connect()
    }

    override fun onDetachedFromWindow() {
        // Releasing this UI connection does not stop playback or the service-owned dance cycle.
        disconnect()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        uiHandler.removeCallbacks(poll)
        if (visibility == View.VISIBLE && isAttachedToWindow) {
            if (connection == null) connect() else scheduleQuery()
        }
    }

    private fun connect() {
        if (connection != null) return
        binding.stopDanceStatus.setText(R.string.stop_dance_connecting)
        val future = MediaController.Builder(
            context.applicationContext,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).setApplicationLooper(Looper.getMainLooper())
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@StopDanceFragment.controller === controller) showUnavailable()
                }
            }).buildAsync()
        connection = future
        uiHandler.postDelayed(timeout, 5000)
        future.addListener({
            if (connection === future && isAttachedToWindow) {
                uiHandler.removeCallbacks(timeout)
                try {
                    controller = future.get()
                    queryState()
                } catch (e: Exception) {
                    showUnavailable()
                }
            }
        }, uiExecutor)
    }

    private fun disconnect() {
        requestVersion++
        uiHandler.removeCallbacksAndMessages(null)
        controller = null
        val oldConnection = connection
        connection = null
        oldConnection?.let { MediaController.releaseFuture(it) }
        commandPending = false
    }

    private fun showUnavailable() {
        disconnect()
        binding.stopDanceStatus.setText(R.string.stop_dance_disconnected)
        binding.stopDanceStatusHint.setText(R.string.stop_dance_source)
        binding.stopDanceTrack.text = ""
        binding.stopDancePrevious.isEnabled = false
        binding.stopDanceNext.isEnabled = false
        binding.stopDanceStart.isEnabled = false
        binding.stopDanceStop.isEnabled = false
    }

    private fun scheduleQuery() {
        uiHandler.removeCallbacks(poll)
        if (isAttachedToWindow && windowVisibility == View.VISIBLE && controller != null) {
            uiHandler.postDelayed(poll, 500)
        }
    }

    private fun queryState() {
        if (!commandPending) sendCommand(CustomCommands.GET_STOP_DANCE_STATE)
    }

    private fun startDance() {
        val min = binding.stopDanceMinInput.text.toString().toIntOrNull()
        val max = binding.stopDanceMaxInput.text.toString().toIntOrNull()
        val pause = binding.stopDancePauseInput.text.toString().toIntOrNull()
        val positiveError = context.getString(R.string.stop_dance_positive_seconds)
        binding.stopDanceMinLayout.error = if (min == null || min <= 0) positiveError else null
        binding.stopDanceMaxLayout.error = when {
            max == null || max <= 0 -> positiveError
            min != null && max < min -> context.getString(R.string.stop_dance_invalid_range)
            else -> null
        }
        binding.stopDancePauseLayout.error = if (pause == null || pause <= 0) positiveError else null
        if (min == null || max == null || pause == null || min <= 0 || max < min || pause <= 0) return

        // Persist validated values only. Invalid or overflowing text must not alter the active run.
        context.config.apply {
            stopDanceMinSeconds = min
            stopDanceMaxSeconds = max
            stopDancePauseSeconds = pause
        }
        sendCommand(CustomCommands.START_STOP_DANCE, Bundle().apply {
            putInt(CustomCommands.MIN_PLAY_SECONDS, min)
            putInt(CustomCommands.MAX_PLAY_SECONDS, max)
            putInt(CustomCommands.PAUSE_SECONDS, pause)
        })
    }

    private fun sendCommand(command: CustomCommands, args: Bundle = Bundle.EMPTY) {
        val client = controller ?: return
        if (!client.isConnected || !client.isSessionCommandAvailable(command.sessionCommand)) {
            showUnavailable()
            return
        }
        val isQuery = command == CustomCommands.GET_STOP_DANCE_STATE
        commandPending = !isQuery
        if (!isQuery) updateControls()
        uiHandler.removeCallbacks(poll)
        uiHandler.removeCallbacks(timeout)
        uiHandler.postDelayed(timeout, 5000)
        // Ignore an older state response after a Start/Stop request, and all responses after detach.
        val version = ++requestVersion
        val future = client.sendCustomCommand(command.sessionCommand, args)
        future.addListener({
            if (version == requestVersion && isAttachedToWindow) {
                uiHandler.removeCallbacks(timeout)
                commandPending = false
                try {
                    val result = future.get()
                    if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                        renderState(result.extras)
                    } else if (isQuery) {
                        showUnavailable()
                    } else {
                        updateControls()
                        binding.stopDanceStatusHint.setText(R.string.stop_dance_failed)
                        Toast.makeText(context, R.string.stop_dance_failed, Toast.LENGTH_LONG).show()
                    }
                    scheduleQuery()
                } catch (e: Exception) {
                    showUnavailable()
                }
            }
        }, uiExecutor)
    }

    private fun renderState(state: Bundle) {
        setTextIfChanged(binding.stopDanceTrack, state.getString(CustomCommands.TRACK_TITLE).orEmpty())
        binding.stopDancePrevious.isEnabled = state.getBoolean(CustomCommands.HAS_PREVIOUS)
        binding.stopDanceNext.isEnabled = state.getBoolean(CustomCommands.HAS_NEXT)
        val phase = state.getString(CustomCommands.STOP_DANCE_PHASE)
        active = phase == StopDanceController.Phase.PLAYING.name || phase == StopDanceController.Phase.PAUSED.name
        canStart = state.getBoolean(CustomCommands.STOP_DANCE_CAN_START)
        val (status, hint) = when (phase) {
            StopDanceController.Phase.PAUSED.name -> R.string.stop_dance_paused to R.string.stop_dance_paused_hint
            StopDanceController.Phase.PLAYING.name -> {
                val title = if (state.getBoolean(CustomCommands.STOP_DANCE_IS_PLAYING)) {
                    R.string.stop_dance_playing
                } else R.string.stop_dance_waiting
                title to R.string.stop_dance_playing_hint
            }
            else -> if (canStart) R.string.stop_dance_ready to R.string.stop_dance_stopped_hint
                else R.string.stop_dance_choose_music to R.string.stop_dance_source
        }
        // Do not emit accessibility live-region changes for identical polling responses.
        setTextIfChanged(binding.stopDanceStatus, context.getString(status))
        setTextIfChanged(binding.stopDanceStatusHint, context.getString(hint))
        if (active) {
            // Another client may have started a run. Show its actual settings, but do not overwrite
            // an idle user's drafts on every poll. Text fields are locked only while running.
            setTextIfChanged(binding.stopDanceMinInput, state.getInt(CustomCommands.MIN_PLAY_SECONDS).toString())
            setTextIfChanged(binding.stopDanceMaxInput, state.getInt(CustomCommands.MAX_PLAY_SECONDS).toString())
            setTextIfChanged(binding.stopDancePauseInput, state.getInt(CustomCommands.PAUSE_SECONDS).toString())
        }
        updateControls()
    }

    private fun updateControls() {
        binding.stopDanceStart.isEnabled = canStart && !active && !commandPending
        binding.stopDanceStop.isEnabled = active && !commandPending
        listOf(binding.stopDanceMinLayout, binding.stopDanceMaxLayout, binding.stopDancePauseLayout).forEach {
            it.isEnabled = !active && !commandPending
        }
    }

    private fun setTextIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        fun tintText(view: View) {
            if (view is TextView) view.setTextColor(textColor)
            if (view is ViewGroup) for (index in 0 until view.childCount) tintText(view.getChildAt(index))
        }
        tintText(binding.stopDanceContent)
        val background = context.getProperBackgroundColor()
        binding.stopDanceStatusCard.setCardBackgroundColor(ColorUtils.blendARGB(background, adjustedPrimaryColor, 0.10f))
        val enabledStates = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled))
        val primaryColors = ColorStateList(enabledStates, intArrayOf(adjustedPrimaryColor, ColorUtils.blendARGB(background, textColor, 0.15f)))
        binding.stopDanceStart.backgroundTintList = primaryColors
        val onPrimary = if (ColorUtils.calculateLuminance(adjustedPrimaryColor) > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        binding.stopDanceStart.setTextColor(ColorStateList(enabledStates, intArrayOf(onPrimary, ColorUtils.blendARGB(background, textColor, 0.4f))))
        binding.stopDanceStop.setTextColor(primaryColors)
        binding.stopDanceStop.strokeColor = primaryColors
        listOf(binding.stopDanceMinLayout, binding.stopDanceMaxLayout, binding.stopDancePauseLayout).forEach {
            it.boxStrokeColor = adjustedPrimaryColor
            it.defaultHintTextColor = ColorStateList.valueOf(textColor)
        }
    }

    // This tab controls playback rather than a searchable/sortable media list.
    override fun setupFragment(activity: BaseSimpleActivity) = Unit
    override fun finishActMode() = Unit
    override fun onSearchQueryChanged(text: String) = Unit
    override fun onSearchClosed() = Unit
    override fun onSortOpen(activity: SimpleActivity) = Unit
}
