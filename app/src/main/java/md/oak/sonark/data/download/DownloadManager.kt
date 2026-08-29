package md.oak.sonark.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import md.oak.sonark.data.AccountSession
import md.oak.sonark.data.SessionManager
import md.oak.sonark.data.Utils
import md.oak.sonark.data.database.AlbumEntity
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.repository.MusicRepository
import java.io.File
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

class DownloadManager(
    private val sessionManager: SessionManager,
    private val repository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observationJob: Job? = null
    
    private val downloadSemaphore = Semaphore(6) 
    private val activeDownloadJobs = Collections.synchronizedMap(mutableMapOf<String, Job>())

    fun start() {
        observationJob?.cancel()
        observationJob = scope.launch {
            sessionManager.currentSession.collectLatest { session ->
                cancelActiveJobs()
                
                if (session == null) return@collectLatest
                
                try {
                    session.songDao.resetAllDownloadingStatus()
                    session.albumDao.resetAllDownloadingStatus()
                    
                    manageDownloads(session)
                } catch (e: CancellationException) {
                    Log.d("DownloadManager", "Session observation cancelled for ${session.email}")
                } catch (e: Exception) {
                    Log.e("DownloadManager", "Error in session observation", e)
                }
            }
        }
    }

    private suspend fun manageDownloads(session: AccountSession) = coroutineScope {
        // Track download observation
        launch {
            session.songDao.getAllSongsFlow().collect { allSongs ->
                val targetTracks = allSongs.filter { 
                    it.downloadStatus == DownloadStatus.PENDING || it.downloadStatus == DownloadStatus.ERROR 
                }
                val targetUrls = targetTracks.map { it.data }.toSet()

                // 1. Cancel jobs for tracks that were paused or completed
                val jobsToCancel = synchronized(activeDownloadJobs) {
                    activeDownloadJobs.keys.filter { it !in targetUrls && !it.startsWith("cover_") }
                }
                jobsToCancel.forEach { url ->
                    activeDownloadJobs.remove(url)?.cancel()
                    Log.d("DownloadManager", "Stop job for track: $url")
                }

                // 2. Start jobs for pending tracks
                targetTracks.forEach { entity ->
                    if (!activeDownloadJobs.containsKey(entity.data)) {
                        processDownload(entity.data, session) {
                            downloadSong(entity, session)
                        }
                    }
                }
            }
        }

        // Album cover observation
        launch {
            session.albumDao.getAlbumsToDownloadFlow().collect { albums ->
                val targetUrls = albums.mapNotNull { it.imageUrl }.toSet()
                
                // 1. Cancel jobs for covers no longer needed
                val jobsToCancel = synchronized(activeDownloadJobs) {
                    activeDownloadJobs.keys.filter { it.startsWith("cover_") && it.removePrefix("cover_") !in targetUrls }
                }
                jobsToCancel.forEach { key ->
                    activeDownloadJobs.remove(key)?.cancel()
                }

                // 2. Start jobs for pending covers
                albums.forEach { entity ->
                    val url = entity.imageUrl ?: return@forEach
                    val key = "cover_$url"
                    if (!activeDownloadJobs.containsKey(key)) {
                        processDownload(key, session) {
                            downloadAlbumCover(entity, session)
                        }
                    }
                }
            }
        }
    }
    
    fun cancelActiveJobs() {
        synchronized(activeDownloadJobs) {
            activeDownloadJobs.values.forEach { it.cancel() }
            activeDownloadJobs.clear()
        }
    }

    private fun processDownload(key: String, session: AccountSession, block: suspend () -> Unit) {
        val job = session.scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                Log.d("DownloadManager", "Task cancelled for $key")
                throw e
            } catch (e: Exception) {
                Log.e("DownloadManager", "Execution failed for $key", e)
                // Fallback: the next flow emission will retry if status is still PENDING/ERROR
                delay(5000) 
            } finally {
                activeDownloadJobs.remove(key)
            }
        }
        activeDownloadJobs[key] = job
    }

    private suspend fun downloadSong(entity: SongEntity, session: AccountSession) {
        val targetFile = File(session.musicDir, extractFileId(entity.data))
        
        if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
            markSongDownloaded(entity, targetFile, session)
            return
        }

        downloadSemaphore.withPermit {
            // Check status again inside semaphore to avoid race with user pause
            val currentStatus = session.songDao.getSongStatusByUrl(entity.data)
            if (currentStatus != DownloadStatus.PENDING && currentStatus != DownloadStatus.ERROR) {
                return@withPermit
            }

            Log.d("DownloadManager", "Starting download: ${entity.title}")
            session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, 0)
            
            try {
                val syncSong = entity.toSyncSong(albumTitle = "Album", imageUrl = null, type = AlbumType.NORMAL)
                performValidatedDownload(syncSong, targetFile, entity.md5Hash, session.scope) { progress ->
                    session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, progress)
                }
                markSongDownloaded(entity, targetFile, session)
            } catch (e: CancellationException) {
                // Do not set status back to PENDING if cancelled (likely by user or session change)
                throw e
            } catch (e: Exception) {
                session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0)
                throw e
            }
        }
    }

    private suspend fun markSongDownloaded(entity: SongEntity, file: File, session: AccountSession) {
        session.songDao.markUrlAsDownloaded(entity.data, file.absolutePath)
        session.scope.launch { repository.fetchMetadata(entity.id) }
    }

    private suspend fun downloadAlbumCover(entity: AlbumEntity, session: AccountSession) {
        val url = entity.imageUrl ?: return
        val targetFile = File(session.musicDir, "cover_" + extractFileId(url))

        if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
            session.albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
            return
        }

        downloadSemaphore.withPermit {
            if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
                session.albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
                return@withPermit
            }

            session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, 0)

            val syncSong = md.oak.sonark.data.model.SyncSong(
                song = md.oak.sonark.data.model.Song(
                    id = "cover_${entity.id}",
                    title = "Cover",
                    artist = entity.artist,
                    album = entity.title,
                    duration = 0
                ),
                data = url,
                albumId = entity.id,
                size = entity.size,
                md5Hash = entity.md5Hash,
                providerId = "google_drive"
            )

            try {
                performValidatedDownload(syncSong, targetFile, entity.md5Hash, session.scope) { progress ->
                    session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, progress)
                }
                session.albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.ERROR, 0)
                throw e
            }
        }
    }

    private suspend fun isAlreadyDownloaded(file: File, expectedHash: String?): Boolean {
        if (!file.exists() || (expectedHash == null)) return false
        return withContext(Dispatchers.IO) {
            Utils.calculateMd5(file) == expectedHash
        }
    }

    private suspend fun performValidatedDownload(
        syncSong: md.oak.sonark.data.model.SyncSong,
        targetFile: File,
        expectedHash: String?,
        sessionScope: CoroutineScope,
        onProgressUpdate: suspend (Int) -> Unit
    ) {
        val provider = repository.getProvider(syncSong.providerId) 
            ?: throw IllegalStateException("Provider ${syncSong.providerId} not found")

        var lastProgressUpdate = 0L
        val success = provider.downloadSong(syncSong, targetFile) { downloaded, total ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 500) {
                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                sessionScope.launch { onProgressUpdate(progress) }
                lastProgressUpdate = currentTime
            }
        }

        if (!success) throw Exception("Download failed")

        if (expectedHash != null) {
            val actualHash = withContext(Dispatchers.IO) { Utils.calculateMd5(targetFile) }
            if (actualHash != expectedHash) throw Exception("MD5 mismatch")
        }
    }

    private fun extractFileId(dataUrl: String): String {
        return dataUrl.substringAfter("files/").substringBefore("?")
            .replace("/", "_")
            .replace(":", "_")
    }
}
