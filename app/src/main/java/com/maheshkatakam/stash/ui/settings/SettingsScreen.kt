@file:OptIn(ExperimentalMaterial3Api::class)

package com.maheshkatakam.stash.ui.settings

import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshkatakam.stash.BuildConfig
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.backup.BackupManager
import com.maheshkatakam.stash.backup.RestoreInfo
import com.maheshkatakam.stash.ui.Biometric
import com.maheshkatakam.stash.ui.components.Format
import com.maheshkatakam.stash.ui.findFragmentActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(app: StashApp, onBack: () -> Unit) {
    val settings by app.settingsState.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restored by remember { mutableStateOf<RestoreInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Saving backup…"
        app.appScope.launch {
            val result = runCatching { BackupManager.export(context.applicationContext, app.repository.db, uri) }
            result.onSuccess {
                app.settings.setLastBackup(System.currentTimeMillis())
            }
            scope.launch {
                busy = null
                result.onSuccess {
                    Toast.makeText(
                        context,
                        "Backup saved: ${it.items} items, ${it.files} files (${Format.size(it.bytes)})",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure { error = "Backup failed: ${it.message ?: "unknown error"}" }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    fun setLock(enable: Boolean) {
        val activity = context.findFragmentActivity() ?: return
        if (!Biometric.isAvailable(context)) {
            if (enable) {
                error = "Set up a fingerprint or a screen lock (PIN, pattern or password) in your phone's settings first."
            } else {
                scope.launch { app.settings.setLock(false) }
            }
            return
        }
        Biometric.authenticate(
            activity = activity,
            title = if (enable) "Turn on Stash lock" else "Turn off Stash lock",
            onSuccess = {
                app.lock.markUnlocked()
                scope.launch { app.settings.setLock(enable) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Section("Privacy")
            SwitchRow(
                title = "Fingerprint lock",
                subtitle = "Ask for your fingerprint (or phone PIN) when opening Stash. Saving from other apps still works while locked.",
                checked = s.lockEnabled,
                onChange = { setLock(it) },
            )
            if (s.lockEnabled) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Lock again after leaving Stash for", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(0 to "Immediately", 60 to "1 minute", 300 to "5 minutes").forEach { (sec, label) ->
                            FilterChip(
                                selected = s.lockTimeoutSec == sec,
                                onClick = { scope.launch { app.settings.setLockTimeout(sec) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Backup & moving to a new phone")
            Text(
                "Save everything (notes, lists, links, photos, voice notes and files) into one backup file. " +
                    "Keep it somewhere safe, like Google Drive or your computer. On a new phone, install Stash, " +
                    "open Settings and choose Restore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                if (s.lastBackupAt > 0) {
                    "Last backup: " + DateUtils.getRelativeTimeSpanString(s.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                } else {
                    "No backup made yet."
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("Stash-backup-$stamp.zip")
                }) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export backup")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restore")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Section("Links")
            SwitchRow(
                title = "Link previews",
                subtitle = "Show the title, picture and post text for links you save. Stash fetches these from the internet.",
                checked = s.previews,
                onChange = { scope.launch { app.settings.setPreviews(it) } },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Appearance")
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    FilterChip(
                        selected = s.theme == value,
                        onClick = { scope.launch { app.settings.setTheme(value) } },
                        label = { Text(label) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(
                    title = "Match wallpaper colors",
                    subtitle = "Use your phone's color theme instead of Stash's indigo.",
                    checked = s.dynamicColor,
                    onChange = { scope.launch { app.settings.setDynamicColor(it) } },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Ways to save quickly")
            listOf(
                "Share from any app (Instagram, X, YouTube, Chrome, Gallery…) and pick Stash.",
                "Select text anywhere, open the ⋮ menu and choose \"Save to Stash\".",
                "Add the Stash widget to your home screen for one-tap notes, lists and voice notes.",
                "Add the \"Stash note\" tile to your Quick Settings panel.",
                "Long-press the Stash app icon for shortcuts.",
            ).forEach {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("•  ", color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                "Stash ${BuildConfig.VERSION_NAME}. Everything is stored only on this phone.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    busy?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(message)
                }
            },
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Everything currently in Stash on this phone will be replaced by the backup's contents. " +
                        "If you have anything here you want to keep, export a backup of it first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    busy = "Reading backup…"
                    app.appScope.launch {
                        val result = runCatching { BackupManager.stageRestore(context.applicationContext, uri) }
                        scope.launch {
                            busy = null
                            result.onSuccess { restored = it }
                                .onFailure { error = it.message ?: "That file couldn't be restored." }
                        }
                    }
                }) { Text("Replace & restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") } },
        )
    }

    restored?.let { info ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup ready") },
            text = {
                Text(
                    "Found ${info.items} items and ${info.files} files. Stash will now restart to finish restoring. " +
                        "If it doesn't reopen by itself, just open it again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.findFragmentActivity()?.let { BackupManager.restartApp(it) }
                }) { Text("Restart now") }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
