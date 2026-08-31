package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import md.oak.sonark.data.repository.SettingsRepository
import md.oak.sonark.data.repository.UserAccount
import java.io.File

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val googleAccountName: StateFlow<String?> = repository.googleAccountName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val storedAccounts: StateFlow<List<UserAccount>> = repository.storedAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _storageUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageUsage: StateFlow<Map<String, Long>> = _storageUsage.asStateFlow()

    init {
        viewModelScope.launch {
            storedAccounts.collect { accounts ->
                calculateStorageUsage(accounts)
            }
        }
    }

    private fun calculateStorageUsage(accounts: List<UserAccount>) {
        viewModelScope.launch {
            val activeEmail = repository.googleAccountName.firstOrNull()
            val usageMap = accounts.associate { account ->
                val musicDir = repository.getAccountMusicDir(account.email)
                val dbFile = repository.getAccountDatabaseFile(account.email)
                
                var size = getDirectorySize(musicDir) + if (dbFile.exists()) dbFile.length() else 0L
                
                // Add legacy/global directory size to the active account
                if (account.email == activeEmail) {
                    repository.getLegacyMusicDir()?.let { size += getDirectorySize(it) }
                    val globalDb = repository.getLegacyDatabaseFile()
                    if (globalDb.exists()) size += globalDb.length()
                }
                
                account.email to size
            }
            _storageUsage.value = usageMap
        }
    }

    private fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        if (directory.isFile) return directory.length()
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    fun clearCache(email: String) {
        viewModelScope.launch {
            val musicDir = repository.getAccountMusicDir(email)
            val dbFile = repository.getAccountDatabaseFile(email)
            
            musicDir.deleteRecursively()
            if (dbFile.exists()) dbFile.delete()
            
            val activeEmail = repository.googleAccountName.firstOrNull()
            if (email == activeEmail) {
                repository.getLegacyMusicDir()?.deleteRecursively()
                val legacyDb = repository.getLegacyDatabaseFile()
                if (legacyDb.exists()) legacyDb.delete()
            }
            
            // Recalculate after clearing
            calculateStorageUsage(storedAccounts.value)
        }
    }

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
            repository.setAccounts(emptyList())
            repository.setGoogleAccountName(null)
        }
    }
}
