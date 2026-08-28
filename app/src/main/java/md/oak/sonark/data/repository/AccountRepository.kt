package md.oak.sonark.data.repository

import android.util.Log
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UserAccount(
    val name: String,
    val email: String,
    val profileImageUrl: String? = null,
    val isPro: Boolean = false
)

data class StorageQuota(
    val usedBytes: Long,
    val totalBytes: Long,
    val usedPercentage: Float
)

class AccountRepository() {

    private val _storageQuota = MutableStateFlow<StorageQuota?>(null)
    val storageQuota: Flow<StorageQuota?> = _storageQuota.asStateFlow()

    private val _accounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val accounts: Flow<List<UserAccount>> = _accounts.asStateFlow()

    suspend fun refreshQuota(driveService: Drive?) = withContext(Dispatchers.IO) {
        if (driveService == null) {
            _storageQuota.value = null
            _accounts.value = emptyList()
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
                // In a real app, you'd manage multiple accounts here.
                // For now, we'll just update the current one's info.
                _accounts.value = listOf(
                    UserAccount(
                        name = user.displayName ?: "User",
                        email = user.emailAddress ?: "",
                        profileImageUrl = user.photoLink,
                        isPro = true // Hardcoded for demo/UI matching
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AccountRepository", "Error refreshing quota", e)
        }
    }
    
    fun setAccounts(accounts: List<UserAccount>) {
        _accounts.value = accounts
    }
}
