package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import md.oak.sonark.data.repository.SettingsRepository

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val localStorageEnabled: Flow<Boolean> = repository.localStorageEnabled

    private val _googleAccountName = MutableStateFlow<String?>(null)
    val googleAccountName: StateFlow<String?> = _googleAccountName

    fun toggleLocalStorage(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLocalStorageEnabled(enabled)
        }
    }

    fun setGoogleAccount(name: String?) {
        _googleAccountName.value = name
    }
}
