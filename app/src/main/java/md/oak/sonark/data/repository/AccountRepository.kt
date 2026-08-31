package md.oak.sonark.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class UserAccount(
    val name: String,
    val email: String,
    val profileImageUrl: String? = null,
    val isPro: Boolean = false,
    val isLoggedIn: Boolean = true,
    val hasConnectionError: Boolean = false,
)

data class StorageQuota(
    val usedBytes: Long,
    val totalBytes: Long,
    val usedPercentage: Float,
)

class AccountRepository(private val settingsRepository: SettingsRepository) {

    private val _storageQuota = MutableStateFlow<StorageQuota?>(null)
    val storageQuota: Flow<StorageQuota?> = _storageQuota.asStateFlow()

    val accounts: Flow<List<UserAccount>> = settingsRepository.storedAccounts

    suspend fun refreshQuota(driveService: Any? = null) {
        // Rust SDK will eventually handle this. 
        // For now, we calculate local usage to provide some feedback.
        val email = settingsRepository.googleAccountName.firstOrNull() ?: return
        val musicDir = settingsRepository.getAccountMusicDir(email)
        val usedBytes = getDirectorySize(musicDir)
        
        // Assume 15GB total as a placeholder for Google Drive free tier
        val totalBytes = 15L * 1024 * 1024 * 1024 
        val usedPercentage = (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
        
        _storageQuota.value = StorageQuota(usedBytes, totalBytes, usedPercentage)
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
}
