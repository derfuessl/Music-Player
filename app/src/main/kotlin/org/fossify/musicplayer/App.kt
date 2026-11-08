package org.fossify.musicplayer

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.fossify.commons.extensions.checkUseEnglish
import org.fossify.musicplayer.helpers.PlaylistListWidget
import org.fossify.musicplayer.helpers.SimpleMediaController
import org.fossify.musicplayer.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        EventBus.getDefault().register(this)
        initController()
        PlaylistListWidget.refreshMetrics(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        PlaylistListWidget.refreshMetrics(this)
    }

    // A playlist can be edited directly from a home-screen widget without opening MainActivity.
    // Observe the existing change event for the whole process so every placed list stays current.
    @Subscribe
    fun onPlaylistsChanged(event: Events.PlaylistsUpdated) {
        PlaylistListWidget.refresh(this)
    }

    private fun initController() {
        SimpleMediaController.getInstance(applicationContext).createControllerAsync()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    SimpleMediaController.destroyInstance()
                }
            }
        )
    }
}
