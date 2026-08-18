package md.oak.sonark.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val GOOGLE_ACCOUNT_NAME = stringPreferencesKey("google_account_name")

    val googleAccountName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GOOGLE_ACCOUNT_NAME]
        }

    suspend fun setGoogleAccountName(name: String?) {
        context.dataStore.edit { preferences ->
            if (name == null) {
                preferences.remove(GOOGLE_ACCOUNT_NAME)
            } else {
                preferences[GOOGLE_ACCOUNT_NAME] = name
            }
        }
    }
}
