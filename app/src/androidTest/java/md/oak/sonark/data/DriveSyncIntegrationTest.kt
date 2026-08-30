package md.oak.sonark.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.utils.TestConfigLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DriveSyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Dependencies.init(context)
        authManager = AuthManager(context)
    }

    @Test
    fun testSyncLibraryWithRealToken() = runBlocking {
        val config = TestConfigLoader.loadConfig()
        
        // Try account_a first, then account_b
        val account = config.account_a ?: config.account_b
        org.junit.Assume.assumeTrue("Skipping test: at least one valid account must be provided", account != null)
        
        account!!

        // Bypass sign-in
        authManager.bypassSignInForTesting(account.email, account.token)
        
        // Trigger session switch
        Dependencies.settingsRepository.setGoogleAccountName(account.email)
        
        // Wait for SessionManager to switch
        withTimeout(15.seconds) {
            SessionManager.currentSession.first { it?.email == account.email }
        }

        // Sync
        Dependencies.musicRepository.syncAll()
        
        // Verify database
        val session = SessionManager.currentSession.value
        assertTrue("Session should be active", session != null)
        
        val songs = session!!.songDao.getAllSongs()
        
        // If account_a failed but account_b is available, maybe try b?
        // Actually the logs showed account_a failed with 'Vault' not found.
        // Let's just make the test fail with a descriptive message if no songs found.
        assertFalse("No songs found in 'Vault' folder for ${account.email}. Please ensure the folder exists.", songs.isEmpty())
    }
}
