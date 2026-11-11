package com.kaif.mastercaller

import android.app.Application
import com.kaif.mastercaller.contacts.ContactHelper
import com.kaif.mastercaller.contacts.WallpaperStore
import com.kaif.mastercaller.update.UpdateManager
import com.kaif.mastercaller.data.SettingsRepository
import com.kaif.mastercaller.notifications.CallNotificationManager
import com.kaif.mastercaller.voice.AnnouncementHelper
import com.kaif.mastercaller.voice.SpeechCommandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons. Created again automatically if Android recreates the process. */
class MasterCallerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var contacts: ContactHelper
        private set
    lateinit var announcer: AnnouncementHelper
        private set
    lateinit var speech: SpeechCommandManager
        private set
    lateinit var wallpapers: WallpaperStore
        private set
    lateinit var updates: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this, appScope)
        contacts = ContactHelper(this)
        wallpapers = WallpaperStore(this)
        updates = UpdateManager(this, appScope)
        // TTS is initialised eagerly so it is warm by the time a call rings.
        announcer = AnnouncementHelper(this, settingsRepository, appScope)
        speech = SpeechCommandManager(this)
        CallNotificationManager.createChannels(this)
    }

    companion object {
        lateinit var instance: MasterCallerApp
            private set
    }
}
