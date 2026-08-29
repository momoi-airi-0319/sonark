package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import md.oak.sonark.data.repository.SettingsRepository
import md.oak.sonark.data.repository.UserAccount

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val googleAccountName: StateFlow<String?> = repository.googleAccountName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val storedAccounts: StateFlow<List<UserAccount>> = repository.storedAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setGoogleAccount(name: String?) {
        viewModelScope.launch {
            repository.setGoogleAccountName(name)
        }
    }

    fun addOrUpdateAccount(account: UserAccount) {
        viewModelScope.launch {
            repository.addOrUpdateAccount(account)
        }
    }

    fun removeAccount(email: String) {
        viewModelScope.launch {
            repository.removeAccount(email)
        }
    }

    fun signOutAll() {
        viewModelScope.launch {
            val accounts = repository.storedAccounts.first()
            val updatedAccounts = accounts.map { it.copy(isLoggedIn = false) }
            repository.setAccounts(updatedAccounts)
            repository.setGoogleAccountName(null)
        }
    }
}
