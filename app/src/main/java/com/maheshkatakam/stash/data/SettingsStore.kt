package com.maheshkatakam.stash.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val lockEnabled: Boolean = false,
    /** How long Stash can stay in the background before it asks for the fingerprint again. */
    val lockTimeoutSec: Int = 60,
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val previews: Boolean = true,
    val gridView: Boolean = true,
    val lastBackupAt: Long = 0L,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val lock = booleanPreferencesKey("lock_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout_sec")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val previews = booleanPreferencesKey("link_previews")
        val grid = booleanPreferencesKey("grid_view")
        val lastBackup = longPreferencesKey("last_backup_at")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        lockEnabled = this[Keys.lock] ?: false,
        lockTimeoutSec = this[Keys.lockTimeout] ?: 60,
        theme = this[Keys.theme] ?: "system",
        dynamicColor = this[Keys.dynamic] ?: true,
        previews = this[Keys.previews] ?: true,
        gridView = this[Keys.grid] ?: true,
        lastBackupAt = this[Keys.lastBackup] ?: 0L,
    )

    suspend fun setLock(enabled: Boolean) = context.dataStore.edit { it[Keys.lock] = enabled }
    suspend fun setLockTimeout(seconds: Int) = context.dataStore.edit { it[Keys.lockTimeout] = seconds }
    suspend fun setTheme(theme: String) = context.dataStore.edit { it[Keys.theme] = theme }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamic] = on }
    suspend fun setPreviews(on: Boolean) = context.dataStore.edit { it[Keys.previews] = on }
    suspend fun setGridView(on: Boolean) = context.dataStore.edit { it[Keys.grid] = on }
    suspend fun setLastBackup(at: Long) = context.dataStore.edit { it[Keys.lastBackup] = at }
}
