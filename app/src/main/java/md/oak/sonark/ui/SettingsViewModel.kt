package md.oak.sonark.ui

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val LOCAL_STORAGE_ENABLED = booleanPreferencesKey("local_storage_enabled")

    val localStorageEnabled: Flow<Boolean> = application.dataStore.data
        .map { preferences ->
            preferences[LOCAL_STORAGE_ENABLED] ?: true
        }

    private val _googleAccountName = MutableStateFlow<String?>(null)
    val googleAccountName: StateFlow<String?> = _googleAccountName

    fun toggleLocalStorage(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { settings ->
                settings[LOCAL_STORAGE_ENABLED] = enabled
            }
        }
    }

    fun setGoogleAccount(name: String?) {
        _googleAccountName.value = name
    }
}
