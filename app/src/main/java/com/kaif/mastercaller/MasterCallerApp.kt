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
import kotlinx.coroutines.launch

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
        syncPendingWallpapers()
    }

    /** Copies one KAVÉ wallpaper into the contact's photo (Google Phone / Contacts). Call off the main thread. */
    fun syncWallpaperToContact(lookupKey: String): Boolean {
        val file = wallpapers.fileForContact(lookupKey) ?: return false
        val ok = contacts.writeContactPhoto(lookupKey, file)
        if (ok) wallpapers.markContactSynced(lookupKey)
        return ok
    }

    /** Catch-up: wallpapers set before the permission/option was on get copied the next time the app starts. */
    fun syncPendingWallpapers() {
        appScope.launch(Dispatchers.IO) {
            if (!settingsRepository.current().syncWallpaperToContacts || !contacts.canWriteContacts()) return@launch
            wallpapers.entries.value.map { it.lookupKey }.filter { wallpapers.needsContactSync(it) }
                .forEach { syncWallpaperToContact(it) }
        }
    }

    companion object {
        lateinit var instance: MasterCallerApp
            private set
    }
}
