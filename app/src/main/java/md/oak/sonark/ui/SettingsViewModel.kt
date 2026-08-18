package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import md.oak.sonark.data.repository.SettingsRepository

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val googleAccountName: StateFlow<String?> = repository.googleAccountName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setGoogleAccount(name: String?) {
        viewModelScope.launch {
            repository.setGoogleAccountName(name)
        }
    }
}
