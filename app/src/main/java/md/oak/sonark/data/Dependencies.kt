package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository
import uniffi.sonark_sdk.SonarkEngine
import uniffi.sonark_sdk.AuthProvider

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object Dependencies {
    lateinit var context: Context
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var accountRepository: AccountRepository
    lateinit var authManager: AuthManager
    lateinit var sonarkEngine: SonarkEngine

    fun init(context: Context) {
        this.context = context.applicationContext
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        if (!::authManager.isInitialized) {
            authManager = AuthManager(context.applicationContext)
        }
        
        if (!::sonarkEngine.isInitialized) {
            System.loadLibrary("uniffi_sonark_sdk")
            val dbFile = context.getDatabasePath("sonark_v2.db")
            dbFile.parentFile?.mkdirs()
            sonarkEngine = SonarkEngine(dbFile.absolutePath)
            
            // Bridge Auth to Rust
            sonarkEngine.setAuthProvider(object : AuthProvider {
                override fun getAccessToken(): String {
                    // Try to get existing token
                    val existingToken = authManager.getLastKnownToken()
                    if (existingToken != null) return existingToken

                    // If missing, try silent sign-in
                    return runBlocking {
                        val email = settingsRepository.googleAccountName.firstOrNull()
                        if (email != null) {
                            android.util.Log.d("SonarkSDK", "Token missing, attempting silent sign-in for $email")
                            authManager.silentSignIn(email)
                        } else {
                            null
                        }
                    } ?: ""
                }
            })
        }

        if (!::accountRepository.isInitialized) {
            accountRepository = AccountRepository(settingsRepository)
        }
        
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(sonarkEngine, settingsRepository)
        }
    }
}
