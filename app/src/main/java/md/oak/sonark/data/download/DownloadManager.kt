package md.oak.sonark.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    
    private val activeDownloadJobs = Collections.synchronizedMap(mutableMapOf<String, Job>())

    fun start() {
        observationJob?.cancel()
        observationJob = scope.launch {
            sessionManager.currentSession.collectLatest { session ->
                cancelActiveJobs()
                
                if (session == null) return@collectLatest
                
                try {
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
        combine(
            session.songDao.getSongsToDownloadFlow(),
            session.albumDao.getAlbumsToDownloadFlow()
        ) { songs, albums ->
            val songTasks = songs.map { 
                DownloadTask(
                    id = it.data,
                    url = it.data,
                    size = it.size,
                    downloadedBytes = it.downloadedBytes,
                    status = it.downloadStatus,
                    isCover = false,
                    entity = it
                )
            }
            val albumTasks = albums.map {
                DownloadTask(
                    id = "cover_${it.imageUrl}",
                    url = it.imageUrl ?: "",
                    size = it.size,
                    downloadedBytes = it.downloadedBytes,
                    status = it.downloadStatus,
                    isCover = true,
                    entity = it
                )
            }
            (songTasks + albumTasks)
                .filter { it.url.isNotEmpty() }
                .sortedBy { it.remainingBytes }
        }.collect { allTasks ->
            val topTasks = allTasks.take(6)
            val topTaskIds = topTasks.map { it.id }.toSet()

            // 1. Cancel jobs for tasks no longer in top 6
            val jobsToCancel = synchronized(activeDownloadJobs) {
                activeDownloadJobs.keys.filter { it !in topTaskIds }
            }
            jobsToCancel.forEach { id ->
                activeDownloadJobs.remove(id)?.cancel()
                Log.d("DownloadManager", "Preempted or finished task: $id")
            }

            // 2. Start jobs for new top tasks
            topTasks.forEach { task ->
                if (!activeDownloadJobs.containsKey(task.id)) {
                    processDownload(task.id, session) {
                        if (task.isCover) {
                            downloadAlbumCover(task.entity as AlbumEntity, session)
                        } else {
                            downloadSong(task.entity as SongEntity, session)
                        }
                    }
                }
            }
        }
    }

    private data class DownloadTask(
        val id: String,
        val url: String,
        val size: Long,
        val downloadedBytes: Long,
        val status: DownloadStatus,
        val isCover: Boolean,
        val entity: Any
    ) {
        val remainingBytes: Long get() = if (size > 0) size - downloadedBytes else Long.MAX_VALUE
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

        // Check status again to avoid race with user pause
        val currentStatus = session.songDao.getSongStatusByUrl(entity.data)
        if (currentStatus == DownloadStatus.NONE || currentStatus == DownloadStatus.PAUSED || currentStatus == DownloadStatus.COMPLETED) {
            return
        }

        Log.d("DownloadManager", "Starting download: ${entity.title}")
        // Maintain existing progress/bytes if any
        session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, entity.downloadProgress, entity.downloadedBytes)
        
        try {
            val syncSong = entity.toSyncSong(albumTitle = "Album", imageUrl = null, type = AlbumType.NORMAL)
            performValidatedDownload(syncSong, targetFile, entity.md5Hash, session.scope) { progress, downloadedBytes ->
                session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, progress, downloadedBytes)
            }
            markSongDownloaded(entity, targetFile, session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0, 0)
            throw e
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

        if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
            session.albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
            return
        }

        session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, entity.downloadProgress, entity.downloadedBytes)

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
            providerId = "google_drive",
            downloadStatus = entity.downloadStatus,
            downloadProgress = entity.downloadProgress,
            downloadedBytes = entity.downloadedBytes
        )

        try {
            performValidatedDownload(syncSong, targetFile, entity.md5Hash, session.scope) { progress, downloadedBytes ->
                session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, progress, downloadedBytes)
            }
            session.albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.albumDao.updateDownloadStatusByUrl(url, DownloadStatus.ERROR, 0, 0)
            throw e
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
        onProgressUpdate: suspend (Int, Long) -> Unit
    ) {
        val provider = repository.getProvider(syncSong.providerId) 
            ?: throw IllegalStateException("Provider ${syncSong.providerId} not found")

        var lastProgressUpdate = 0L
        val success = provider.downloadSong(syncSong, targetFile) { downloaded, total ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 500) {
                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                sessionScope.launch { onProgressUpdate(progress, downloaded) }
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
