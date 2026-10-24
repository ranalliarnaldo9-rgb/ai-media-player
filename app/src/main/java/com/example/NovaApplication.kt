package com.example

import android.app.Application
import com.example.database.NovaDatabase
import com.example.playback.PlaybackManager
import com.example.settings.SettingsManager

class NovaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize singletons
        NovaDatabase.getDatabase(this)
        PlaybackManager.getPlaybackManager(this)
        SettingsManager.getSettingsManager(this)
    }
}
