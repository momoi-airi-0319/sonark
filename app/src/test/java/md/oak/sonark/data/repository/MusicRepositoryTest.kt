package md.oak.sonark.data.repository

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import uniffi.sonark_sdk.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val googleAccountNameFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.googleAccountName } returns googleAccountNameFlow
        every { settingsRepository.getAccountDatabaseFile(any()) } answers { File("/tmp/sonark_${it.invocation.args[0]}.db") }
        every { settingsRepository.getLegacyDatabaseFile() } returns File("/tmp/sonark_legacy.db")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `test sync status transitions`() = runTest {
        val engine: SonarkEngineInterface = mockk(relaxed = true)
        val repository = MusicRepository(engine, settingsRepository)

        repository.syncStatus.test {
            assertEquals(SyncStatus.Idle, awaitItem())

            repository.syncAll()
            assertEquals(SyncStatus.Syncing, awaitItem())

            // Simulate sync complete callback
            val observerSlot = slot<SonarkObserver>()
            verify { engine.setObserver(capture(observerSlot)) }
            observerSlot.captured.onSyncComplete(emptyList())

            val successItem = awaitItem()
            assertTrue(successItem is SyncStatus.Success)
            assertEquals(0, (successItem as SyncStatus.Success).songCount)
        }
    }

    @Test
    fun `test engine switching on account change`() = runTest {
        val engineFactory: (File) -> SonarkEngineInterface = mockk()
        val engine1: SonarkEngineInterface = mockk(relaxed = true)
        val engine2: SonarkEngineInterface = mockk(relaxed = true)

        every { engineFactory(any()) } answers {
            val file = it.invocation.args[0] as File
            if (file.name.contains("user1")) engine1 else engine2
        }

        val repository = MusicRepository(settingsRepository, engineFactory)

        // Switch to user1
        googleAccountNameFlow.value = "user1@example.com"
        verify { engineFactory(match { it.name.contains("user1") }) }
        verify { engine1.setObserver(any()) }

        // Switch to user2
        googleAccountNameFlow.value = "user2@example.com"
        verify { engineFactory(match { it.name.contains("user2") }) }
        verify { engine2.setObserver(any()) }
    }
}
