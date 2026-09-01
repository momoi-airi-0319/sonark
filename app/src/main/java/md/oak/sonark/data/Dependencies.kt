package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository
import uniffi.sonark_sdk.SonarkEngine
import uniffi.sonark_sdk.SonarkEngineInterface
import uniffi.sonark_sdk.AuthProvider

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.File

object Dependencies {
    lateinit var context: Context
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var accountRepository: AccountRepository
    lateinit var authManager: AuthManager

    fun createEngine(dbFile: File): SonarkEngineInterface {
        dbFile.parentFile?.mkdirs()
        val engine = SonarkEngine(dbFile.absolutePath)
        
        // Bridge Auth to Rust
        engine.setAuthProvider(object : AuthProvider {
            override fun getAccessToken(): String {
                val existingToken = authManager.getLastKnownToken()
                if (existingToken != null) return existingToken

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
        return engine
    }

    fun init(context: Context) {
        this.context = context.applicationContext
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        if (!::authManager.isInitialized) {
            authManager = AuthManager(context.applicationContext)
        }
        if (!::accountRepository.isInitialized) {
            accountRepository = AccountRepository(settingsRepository)
        }
        if (!::musicRepository.isInitialized) {
            System.loadLibrary("uniffi_sonark_sdk")
            musicRepository = MusicRepository(
                settingsRepository = settingsRepository,
                engineFactory = { dbFile -> createEngine(dbFile) }
            )
        }
    }
}
