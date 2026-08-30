package md.oak.sonark.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.Dependencies
import md.oak.sonark.data.SessionManager
import md.oak.sonark.utils.TestConfigLoader
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSwitchStressTest {

    private lateinit var context: Context
    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Dependencies.init(context)
        authManager = AuthManager(context)
    }

    @Test
    fun testRapidAccountSwitchingStability() = runBlocking {
        val config = TestConfigLoader.loadConfig()
        val accA = config.account_a
        val accB = config.account_b
        
        org.junit.Assume.assumeTrue("Skipping test: account_a and account_b credentials must be provided", accA != null && accB != null)
        
        accA!!; accB!!

        // Start a background sync that might take time
        authManager.bypassSignInForTesting(accA.email, accA.token)
        Dependencies.settingsRepository.setGoogleAccountName(accA.email)
        
        val syncJob = launch(Dispatchers.IO) {
            repeat(5) {
                try {
                    Dependencies.musicRepository.syncAll()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                delay(200)
            }
        }

        // Rapidly switch accounts
        repeat(10) {
            val target = if (it % 2 == 0) accB else accA
            authManager.bypassSignInForTesting(target.email, target.token)
            Dependencies.settingsRepository.setGoogleAccountName(target.email)
            delay(300) // Moderate speed to ensure some real IO happens
        }

        syncJob.cancelAndJoin()
        
        // Final switch to ensure stability
        Dependencies.settingsRepository.setGoogleAccountName(accA.email)
        delay(1000)
    }
}
