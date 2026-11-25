package com.maheshkatakam.stash

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.maheshkatakam.stash.backup.BackupManager
import com.maheshkatakam.stash.data.AppSettings
import com.maheshkatakam.stash.data.FileStore
import com.maheshkatakam.stash.data.SettingsStore
import com.maheshkatakam.stash.data.StashDatabase
import com.maheshkatakam.stash.data.StashRepository
import com.maheshkatakam.stash.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StashApp : Application(), ImageLoaderFactory {

    /** Work that must finish even after a screen closes (saving text, deleting files). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: StashRepository
        private set
    lateinit var settings: SettingsStore
        private set

    /** Null until the saved settings have loaded, so the lock can't flash content before it knows. */
    lateinit var settingsState: StateFlow<AppSettings?>
        private set

    val lock = LockState()

    override fun onCreate() {
        super.onCreate()
        // A restore picked in Settings is applied here, before the database is opened.
        BackupManager.applyPendingRestore(this)

        repository = StashRepository(this, StashDatabase.build(this), FileStore(this), appScope)
        settings = SettingsStore(this)
        settingsState = settings.flow
            .map<AppSettings, AppSettings?> { it }
            .stateIn(appScope, SharingStarted.Eagerly, null)

        Notifications.createChannels(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = lock.onForeground(settingsState.value)
            override fun onStop(owner: LifecycleOwner) = lock.onBackground()
        })

        appScope.launch {
            runCatching { repository.rescheduleAllReminders() }
            runCatching { repository.purgeOldTrash() }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}

/** Tracks whether the fingerprint lock is currently open. */
class LockState {
    var unlocked by mutableStateOf(false)
        private set

    /** True while the fingerprint/PIN prompt is showing (the PIN screen briefly puts Stash in the background). */
    var authInProgress = false

    /** The lock screen prompts automatically once each time it appears. */
    var autoPrompted = false

    private var backgroundAt = 0L

    fun markUnlocked() {
        unlocked = true
        authInProgress = false
        backgroundAt = 0L
    }

    fun onBackground() {
        if (!authInProgress) backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(settings: AppSettings?) {
        if (settings == null || !settings.lockEnabled || !unlocked || backgroundAt == 0L) return
        if (SystemClock.elapsedRealtime() - backgroundAt >= settings.lockTimeoutSec * 1000L) {
            unlocked = false
            autoPrompted = false
        }
        backgroundAt = 0L
    }
}
