package md.oak.sonark.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val LOCAL_STORAGE_ENABLED = booleanPreferencesKey("local_storage_enabled")

    val localStorageEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[LOCAL_STORAGE_ENABLED] ?: true
        }

    suspend fun setLocalStorageEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[LOCAL_STORAGE_ENABLED] = enabled
        }
    }
}
