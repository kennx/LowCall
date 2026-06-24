package cc.niaoer.lowcall.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lowcallDataStore by preferencesDataStore("lowcall_settings")

class SettingsRepository(private val context: Context) {
    private val notificationEnabledKey = booleanPreferencesKey("notification_enabled")

    val notificationEnabled: Flow<Boolean> = context.lowcallDataStore.data
        .map { prefs -> prefs[notificationEnabledKey] ?: true }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.lowcallDataStore.edit { it[notificationEnabledKey] = enabled }
    }
}
