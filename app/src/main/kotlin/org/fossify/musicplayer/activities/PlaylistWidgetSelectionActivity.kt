package org.fossify.musicplayer.activities

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.helpers.PlaylistListWidget
import org.fossify.musicplayer.helpers.PlaylistTileWidget
import org.fossify.musicplayer.models.Playlist

/** Android calls the same screen for placement and later reconfiguration. Keep all changes
 * local to this screen until OK, so cancelling a resize-menu edit preserves the existing widget. */
class PlaylistWidgetSelectionActivity : SimpleActivity() {
    private var color = PlaylistListWidget.DEFAULT_COLOR
    private var transparency = 0
    private var heading = false
    private var automatic = true
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        val manager = AppWidgetManager.getInstance(this)
        val provider = when (manager.getAppWidgetInfo(id)?.provider) {
            ComponentName(this, PlaylistListWidget::class.java) -> PlaylistListWidget()
            ComponentName(this, PlaylistTileWidget::class.java) -> PlaylistTileWidget()
            else -> { finish(); return }
        }
        val prefs = PlaylistListWidget.preferences(this)
        val savedIds = prefs.getStringSet(id.toString(), null)
        automatic = savedInstanceState?.getBoolean("automatic") ?: (savedIds == null)
        selected.addAll(savedInstanceState?.getStringArrayList("selection") ?: savedIds.orEmpty().toList())
        color = savedInstanceState?.getInt("color") ?: prefs.getInt("color:$id", PlaylistListWidget.DEFAULT_COLOR)
        transparency = savedInstanceState?.getInt("transparency") ?: prefs.getInt("transparency:$id", 0)
        heading = savedInstanceState?.getBoolean("heading") ?: prefs.getBoolean("heading:$id", false)
        ensureBackgroundThread {
            val playlists = audioHelper.getAllPlaylists().sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
            runOnUiThread {
                val title = if (provider is PlaylistTileWidget) R.string.widget_playlist_tiles else R.string.widget_playlist_label
                if (!isFinishing && !isDestroyed) showSettings(playlists, title) {
                    val editor = prefs.edit().putInt("color:$id", color).putInt("transparency:$id", transparency)
                        .putBoolean("heading:$id", heading)
                    if (automatic) editor.remove(id.toString()) else editor.putStringSet(id.toString(), selected.toSet())
                    editor.apply()
                    // The host skips the first onUpdate when configuration is required.
                    provider.onUpdate(this, manager, intArrayOf(id))
                    setResult(RESULT_OK, result)
                }
            }
        }
    }

    private fun showSettings(playlists: List<Playlist>, title: Int, save: () -> Unit) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        val colorButton = Button(this)
        fun displayColor() {
            colorButton.setText(R.string.widget_panel_color)
            colorButton.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            colorButton.setTextColor(PlaylistListWidget.textColor(color))
        }
        displayColor()
        colorButton.setOnClickListener {
            ColorPickerDialog(this, color) { confirmed, picked ->
                if (confirmed) {
                    color = picked or Color.BLACK
                    displayColor()
                }
            }
        }
        column.addView(colorButton)
        val label = TextView(this)
        fun updateLabel() { label.text = getString(R.string.widget_transparency, transparency) }
        updateLabel()
        column.addView(label)
        column.addView(SeekBar(this).apply {
            max = 100
            progress = transparency
            contentDescription = getString(R.string.widget_transparency_label)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    transparency = value
                    updateLabel()
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
        column.addView(CheckBox(this).apply {
            setText(R.string.widget_show_title)
            isChecked = heading
            setOnCheckedChangeListener { _, value -> heading = value }
        })
        column.addView(TextView(this).apply { text = getString(R.string.widget_playlist_limit, PlaylistListWidget.LIMIT) })
        val auto = CheckBox(this).apply { setText(R.string.widget_playlist_auto); isChecked = automatic }
        column.addView(auto)

        // Automatic mode follows the first six available playlists. Tapping a row converts that
        // visible selection into a fixed list. Removed playlists must not consume a selection slot.
        selected.retainAll(playlists.map { it.id.toString() }.toSet())
        val boxes = playlists.map { playlist ->
            CheckBox(this).apply { text = playlist.title; column.addView(this) }
        }
        fun selectDefaults() {
            selected.clear()
            playlists.take(PlaylistListWidget.LIMIT).forEach { selected.add(it.id.toString()) }
        }
        fun displaySelection() {
            boxes.forEachIndexed { index, box -> box.isChecked = playlists[index].id.toString() in selected }
        }
        if (automatic) selectDefaults()
        displaySelection()
        auto.setOnClickListener {
            automatic = auto.isChecked
            if (automatic) { selectDefaults(); displaySelection() }
        }
        boxes.forEachIndexed { index, box ->
            box.setOnClickListener {
                val key = playlists[index].id.toString()
                if (box.isChecked && selected.size >= PlaylistListWidget.LIMIT) {
                    box.isChecked = false
                    Toast.makeText(this, getString(R.string.widget_playlist_limit, PlaylistListWidget.LIMIT), Toast.LENGTH_SHORT).show()
                } else {
                    automatic = false
                    auto.isChecked = false
                    if (box.isChecked) selected.add(key) else selected.remove(key)
                }
            }
        }
        AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { finish() }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("color", color)
        outState.putInt("transparency", transparency)
        outState.putBoolean("heading", heading)
        outState.putBoolean("automatic", automatic)
        outState.putStringArrayList("selection", ArrayList(selected))
    }
}
