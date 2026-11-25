package com.maheshkatakam.stash

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshkatakam.stash.ui.Biometric
import com.maheshkatakam.stash.ui.StashNavHost
import com.maheshkatakam.stash.ui.lock.LockScreen
import com.maheshkatakam.stash.ui.theme.StashTheme

class MainActivity : FragmentActivity() {

    private val app get() = application as StashApp

    /** An item to open once the app is unlocked (from a reminder notification). */
    private var pendingOpenId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by app.settingsState.collectAsStateWithLifecycle()
            val s = settings
            val dark = when (s?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(s?.lockEnabled) {
                // With the lock on, hide Stash's contents in the recent-apps screen.
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(s?.lockEnabled != true)
            }
            StashTheme(dark = dark, dynamicColor = s?.dynamicColor ?: true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        s == null -> Unit
                        s.lockEnabled && !app.lock.unlocked -> LockScreen(
                            onUnlock = ::unlock,
                            autoPrompt = !app.lock.autoPrompted,
                            onAutoPrompted = { app.lock.autoPrompted = true },
                        )
                        else -> StashNavHost(
                            app = app,
                            openItemId = pendingOpenId,
                            onOpenConsumed = { pendingOpenId = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_ITEM, -1L) ?: -1L
        if (id > 0) pendingOpenId = id
    }

    private fun unlock() {
        val lock = app.lock
        if (lock.authInProgress) return
        if (!Biometric.isAvailable(this)) {
            // The phone's screen lock was removed, so there's nothing to check against.
            lock.markUnlocked()
            Toast.makeText(this, "No fingerprint or screen lock is set up, so Stash opened without it.", Toast.LENGTH_LONG).show()
            return
        }
        lock.authInProgress = true
        Biometric.authenticate(
            activity = this,
            title = "Unlock Stash",
            onSuccess = { lock.markUnlocked() },
            onFailure = { lock.authInProgress = false },
        )
    }

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item_id"
    }
}
