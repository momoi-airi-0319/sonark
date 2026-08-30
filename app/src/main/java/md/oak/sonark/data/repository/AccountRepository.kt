package md.oak.sonark.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

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
        // For now, we stub it to keep the UI from crashing.
        _storageQuota.value = StorageQuota(0, 100, 0f)
    }
}
