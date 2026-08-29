package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.repository.SettingsRepository
import md.oak.sonark.data.repository.UserAccount
import java.io.File

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _accountStorageUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val accountStorageUsage: StateFlow<Map<String, Long>> = _accountStorageUsage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.storedAccounts.collect { 
                refreshStorageUsage()
            }
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            val accounts = repository.storedAccounts.first()
            val usageMap = accounts.associate { account ->
                account.email to calculateAccountStorage(account.email)
            }
            _accountStorageUsage.value = usageMap
        }
    }

    private fun calculateAccountStorage(email: String): Long {
        val musicDir = repository.getAccountMusicDir(email)
        val dbFile = repository.getAccountDatabaseFile(email)
        
        var total = 0L
        total += musicDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (dbFile.exists()) total += dbFile.length()
        
        // Also check for -shm and -wal files for Room
        val shmFile = File(dbFile.absolutePath + "-shm")
        if (shmFile.exists()) total += shmFile.length()
        val walFile = File(dbFile.absolutePath + "-wal")
        if (walFile.exists()) total += walFile.length()
        
        return total
    }

    fun clearCache(email: String) {
        viewModelScope.launch {
            val activeEmail = repository.googleAccountName.first()
            
            if (activeEmail == email) {
                // If it's the active account, we MUST drop the session first
                // to stop all background tasks and Flow observations.
                repository.setGoogleAccountName(null)
                delay(500L) // Wait for SessionManager to process and stop tasks
            }

            // Close the database instance and remove from cache.
            SonarkDatabase.closeAndRemoveInstance(email)
            
            val musicDir = repository.getAccountMusicDir(email)
            val dbFile = repository.getAccountDatabaseFile(email)
            
            // Delete physical files
            musicDir.deleteRecursively()
            dbFile.delete()
            File(dbFile.absolutePath + "-shm").delete()
            File(dbFile.absolutePath + "-wal").delete()

            if (activeEmail == email) {
                // Re-enable the account, which will open a fresh DB
                repository.setGoogleAccountName(activeEmail)
            }
            
            refreshStorageUsage()
        }
    }

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
