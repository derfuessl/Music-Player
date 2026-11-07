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
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import org.fossify.musicplayer.extensions.playlistDAO
import java.util.concurrent.Executors
import org.fossify.musicplayer.helpers.PlaylistListWidget
import org.fossify.musicplayer.helpers.PlaylistTileWidget
import org.fossify.musicplayer.helpers.WidgetPlaylistIndex

/** Android calls the same screen for placement and later reconfiguration. Keep all changes
 * local to this screen until OK, so cancelling a resize-menu edit preserves the existing widget. */
class PlaylistWidgetSelectionActivity : SimpleActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var query = ""
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
        query = savedInstanceState?.getString("query").orEmpty()
        val prefs = PlaylistListWidget.preferences(this)
        val savedIds = prefs.getStringSet(id.toString(), null)
        automatic = savedInstanceState?.getBoolean("automatic") ?: (savedIds == null)
        selected.addAll(savedInstanceState?.getStringArrayList("selection") ?: savedIds.orEmpty().toList())
        color = savedInstanceState?.getInt("color") ?: prefs.getInt("color:$id", PlaylistListWidget.DEFAULT_COLOR)
        transparency = savedInstanceState?.getInt("transparency") ?: prefs.getInt("transparency:$id", 0)
        heading = savedInstanceState?.getBoolean("heading") ?: prefs.getBoolean("heading:$id", false)
        ensureBackgroundThread {
            val playlists = WidgetPlaylistIndex(playlistDAO.widgetPlaylists(Int.MAX_VALUE))
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

    private fun showSettings(playlists: WidgetPlaylistIndex, title: Int, save: () -> Unit) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true // don't bring up the keyboard before the user taps Search
        }
        val search = EditText(this).apply {
            setSingleLine()
            setHint(R.string.widget_search)
            setText(query)
            setSelection(text.length)
        }
        panel.addView(search)
        val empty = TextView(this).apply { setText(R.string.widget_search_empty); visibility = View.GONE }
        panel.addView(empty)
        val list = ListView(this)
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.addHeaderView(column, null, false)
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
        selected.retainAll(playlists.keys)
        fun selectDefaults() {
            selected.clear()
            playlists.rows.take(PlaylistListWidget.LIMIT).forEach { selected.add(it.key) }
        }
        if (automatic) selectDefaults()
        var matches = if (query.isBlank()) playlists.rows else emptyList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = matches.size
            override fun getItem(position: Int) = matches[position]
            override fun getItemId(position: Int) = getItem(position).key.toLong()
            override fun hasStableIds() = true

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = getItem(position)
                // Bind both title and check state on every reuse. Selection belongs to a stable
                // database ID, never to the filtered position or to a recycled CheckBox.
                return (convertView as? CheckBox ?: CheckBox(parent.context)).apply {
                    text = row.title
                    isChecked = row.key in selected
                    setOnClickListener {
                        if (isChecked && selected.size >= PlaylistListWidget.LIMIT) {
                            isChecked = false
                            Toast.makeText(context, getString(R.string.widget_playlist_limit, PlaylistListWidget.LIMIT), Toast.LENGTH_SHORT).show()
                        } else {
                            automatic = false
                            auto.isChecked = false
                            if (isChecked) selected.add(row.key) else selected.remove(row.key)
                        }
                    }
                }
            }
        }
        list.adapter = adapter
        auto.setOnClickListener {
            automatic = auto.isChecked
            if (automatic) { selectDefaults(); adapter.notifyDataSetChanged() }
        }
        var generation = 0
        fun filter(text: String) {
            query = text
            val latest = ++generation
            // Coalesce rapid keystrokes and reject late results after changes or rotation.
            // This handler is private to the picker, so removing callbacks affects only its work.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                worker.execute {
                    val results = playlists.search(text)
                    runOnUiThread {
                        if (isDestroyed || isFinishing || latest != generation) return@runOnUiThread
                        matches = results
                        column.visibility = if (text.isBlank()) View.VISIBLE else View.GONE
                        empty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                        adapter.notifyDataSetChanged()
                        list.setSelection(0)
                    }
                }
            }, 100)
        }
        search.doAfterTextChanged { filter(it.toString()) }
        filter(query)
        AlertDialog.Builder(this).setTitle(title).setView(panel)
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { finish() }.show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", query)
        outState.putInt("color", color)
        outState.putInt("transparency", transparency)
        outState.putBoolean("heading", heading)
        outState.putBoolean("automatic", automatic)
        outState.putStringArrayList("selection", ArrayList(selected))
    }
}
