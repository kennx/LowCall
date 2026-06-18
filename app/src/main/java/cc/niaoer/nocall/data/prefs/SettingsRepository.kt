package cc.niaoer.nocall.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nocallDataStore by preferencesDataStore("nocall_settings")

class SettingsRepository(private val context: Context) {
    private val notificationEnabledKey = booleanPreferencesKey("notification_enabled")

    val notificationEnabled: Flow<Boolean> = context.nocallDataStore.data
        .map { prefs -> prefs[notificationEnabledKey] ?: true }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.nocallDataStore.edit { it[notificationEnabledKey] = enabled }
    }
}
