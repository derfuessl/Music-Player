package org.fossify.musicplayer.activities

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.helpers.PlaylistListWidget

/** Edits one placed widget. Cancel leaves its previous selection intact. */
class PlaylistWidgetSelectionActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val prefs = PlaylistListWidget.preferences(this)
        ensureBackgroundThread {
            val playlists = audioHelper.getAllPlaylists().sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val saved = prefs.getStringSet(id.toString(), null)
                val checked = BooleanArray(playlists.size) { index ->
                    saved?.contains(playlists[index].id.toString()) ?: (index < PlaylistListWidget.LIMIT)
                }
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.widget_playlist_limit, PlaylistListWidget.LIMIT))
                    .setMultiChoiceItems(playlists.map { it.title }.toTypedArray(), checked) { dialog, position, enabled ->
                        // Android has already changed the supplied array when this listener runs.
                        if (enabled && checked.count { it } > PlaylistListWidget.LIMIT) {
                            checked[position] = false
                            (dialog as AlertDialog).listView.setItemChecked(position, false)
                            android.widget.Toast.makeText(this, getString(R.string.widget_playlist_limit, PlaylistListWidget.LIMIT),
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val ids = playlists.indices.filter { checked[it] }.map { playlists[it].id.toString() }.toSet()
                        prefs.edit().putStringSet(id.toString(), ids).apply()
                        PlaylistListWidget.refresh(this)
                    }
                    .setNeutralButton(R.string.widget_playlist_auto) { _, _ ->
                        prefs.edit().remove(id.toString()).apply()
                        PlaylistListWidget.refresh(this)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener { finish() }.show()
            }
        }
    }
}
