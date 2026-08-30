package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository
import uniffi.sonark_sdk.SonarkEngine
import uniffi.sonark_sdk.AuthProvider

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var accountRepository: AccountRepository
    lateinit var authManager: AuthManager
    lateinit var sonarkEngine: SonarkEngine

    fun init(context: Context) {
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        if (!::authManager.isInitialized) {
            authManager = AuthManager(context.applicationContext)
        }
        
        if (!::sonarkEngine.isInitialized) {
            val dbFile = context.getDatabasePath("sonark_v2.db")
            dbFile.parentFile?.mkdirs()
            sonarkEngine = SonarkEngine(dbFile.absolutePath)
            
            // Bridge Auth to Rust
            sonarkEngine.setAuthProvider(object : AuthProvider {
                override fun getAccessToken(): String {
                    // Rust Engine will call this when it needs a token for Google Drive API.
                    // We return the last known valid token from our AuthManager.
                    return authManager.getLastKnownToken() ?: ""
                }
            })
        }

        if (!::accountRepository.isInitialized) {
            accountRepository = AccountRepository(settingsRepository)
        }
        
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(sonarkEngine)
        }
    }
}
