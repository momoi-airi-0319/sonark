package md.oak.sonark.data.repository

import android.util.Log
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

    suspend fun refreshQuota(driveService: Drive?) = withContext(Dispatchers.IO) {
        if (driveService == null) {
            _storageQuota.value = null
            return@withContext
        }
        try {
            val about = driveService.about().get().setFields("storageQuota, user").execute()
            val quota = about.storageQuota
            if (quota != null) {
                val used = quota.usage ?: 0L
                val limit = quota.limit ?: 0L
                _storageQuota.value = StorageQuota(
                    usedBytes = used,
                    totalBytes = limit,
                    usedPercentage = if (limit > 0) used.toFloat() / limit.toFloat() else 0f
                )
            }
            
            val user = about.user
            if (user != null) {
                val email = user.emailAddress ?: ""
                val account = UserAccount(
                    name = user.displayName ?: "User",
                    email = email,
                    profileImageUrl = user.photoLink,
                    isPro = true,
                    isLoggedIn = true,
                )
                settingsRepository.addOrUpdateAccount(account)
            }
        } catch (e: Exception) {
            Log.e("AccountRepository", "Error refreshing quota", e)
            throw e
        }
    }
}
