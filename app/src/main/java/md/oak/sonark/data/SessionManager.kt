package md.oak.sonark.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.repository.SettingsRepository
import java.io.File
import android.os.Environment
import kotlin.time.Duration.Companion.seconds

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentSession = MutableStateFlow<AccountSession?>(null)
    val currentSession = _currentSession.asStateFlow()
    
    private val pendingCleanups = mutableMapOf<String, Job>()
    private var isInitialized = false

    fun init(context: Context, settingsRepository: SettingsRepository) {
        if (isInitialized) return
        isInitialized = true
        
        scope.launch {
            settingsRepository.googleAccountName.collectLatest { email ->
                switchSessionInternal(context, email)
            }
        }
    }

    private fun switchSessionInternal(context: Context, email: String?) {
        val oldSession = _currentSession.value
        if (oldSession?.email == email) return

        // If we are switching to an email that has a pending cleanup, cancel that cleanup
        email?.let { pendingCleanups.remove(it)?.cancel() }

        if (email == null) {
            _currentSession.value = null
            oldSession?.let { stopSession(it) }
            return
        }

        val db = SonarkDatabase.getDatabase(context, email)
        val accountHash = email.hashCode().toString()
        val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music/$accountHash").apply {
            if (!exists()) mkdirs()
        }

        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newSession = AccountSession(
            email = email,
            database = db,
            songDao = db.songDao(),
            albumDao = db.albumDao(),
            musicDir = musicDir,
            scope = sessionScope
        )

        _currentSession.value = newSession
        
        if (oldSession != null) {
            stopSession(oldSession)
        }
    }

    private fun stopSession(session: AccountSession) {
        // 1. Cancel all operations belonging to the old session
        session.scope.cancel()
        
        // 2. Schedule database closure after a grace period
        val email = session.email
        val db = session.database
        val cleanupJob = cleanupScope.launch {
            delay(2.seconds)
            try {
                SonarkDatabase.closeAndRemoveInstance(email, db)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                synchronized(pendingCleanups) {
                    if (pendingCleanups[email] == coroutineContext[Job]) {
                        pendingCleanups.remove(email)
                    }
                }
            }
        }
        
        synchronized(pendingCleanups) {
            pendingCleanups.remove(email)?.cancel()
            pendingCleanups[email] = cleanupJob
        }
    }
}
