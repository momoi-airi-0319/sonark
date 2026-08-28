package md.oak.sonark.data.download

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import md.oak.sonark.data.Utils
import md.oak.sonark.data.database.AlbumDao
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.repository.MusicRepository
import java.io.File
import java.util.Collections

class DownloadManager(
    private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music").apply { if (!exists()) mkdirs() }
    
    private val activeDownloads = Collections.synchronizedSet(mutableSetOf<String>())
    private val downloadSemaphore = Semaphore(6)

    fun start() {
        startSongDownloads()
        startAlbumDownloads()
    }

    private fun startSongDownloads() {
        scope.launch {
            songDao.getSongsToDownloadFlow().collect { entities ->
                val uniqueFiles = entities
                    .filter { it.downloadStatus == DownloadStatus.PENDING }
                    .groupBy { it.data }
                    .values
                    .map { it.first() }
                    .sortedBy { it.size }

                uniqueFiles.forEach { entity ->
                    if (activeDownloads.add(entity.data)) {
                        scope.launch {
                            try {
                                downloadSemaphore.withPermit {
                                    try {
                                        downloadWithRetry(3) { downloadSong(entity) }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0)
                                    }
                                }
                            } finally {
                                activeDownloads.remove(entity.data)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startAlbumDownloads() {
        scope.launch {
            albumDao.getAlbumsToDownloadFlow().collect { entities ->
                entities.forEach { entity ->
                    val url = entity.imageUrl ?: return@forEach
                    if (activeDownloads.add(url)) {
                        scope.launch {
                            try {
                                downloadSemaphore.withPermit {
                                    try {
                                        downloadWithRetry(3) { downloadAlbumCover(entity) }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        albumDao.updateDownloadStatusByUrl(url, DownloadStatus.ERROR, 0)
                                    }
                                }
                            } finally {
                                activeDownloads.remove(url)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadWithRetry(maxAttempts: Int, block: suspend () -> Unit) {
        var attempt = 0
        while (attempt < maxAttempts) {
            try {
                block()
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                val delayTime = (1000L * attempt * attempt) // Exponential backoff: 1s, 4s, 9s...
                delay(delayTime)
            }
        }
    }

    private suspend fun downloadSong(entity: md.oak.sonark.data.database.SongEntity) {
        val fileId = extractFileId(entity.data)
        val targetFile = File(musicDir, fileId)
        
        if (targetFile.exists() && entity.md5Hash != null) {
            val actualHash = Utils.calculateMd5(targetFile)
            if (actualHash == entity.md5Hash) {
                songDao.markUrlAsDownloaded(entity.data, targetFile.absolutePath)
                scope.launch { repository.fetchMetadata(entity.id) }
                return
            }
        }
        
        val provider = repository.getProvider(entity.providerId) ?: throw Exception("Provider not found")
        
        songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, 0)
        
        val syncSong = entity.toSyncSong("Album", null, AlbumType.NORMAL)
        
        val success = performDownload(syncSong, targetFile) { progress ->
            scope.launch {
                songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, progress)
            }
        }

        if (success) {
            val actualHash = Utils.calculateMd5(targetFile)
            if (entity.md5Hash == null || actualHash == entity.md5Hash) {
                songDao.markUrlAsDownloaded(entity.data, targetFile.absolutePath)
                scope.launch { repository.fetchMetadata(entity.id) }
            } else {
                throw Exception("MD5 mismatch")
            }
        } else {
            throw Exception("Download failed")
        }
    }

    private suspend fun downloadAlbumCover(entity: md.oak.sonark.data.database.AlbumEntity) {
        val url = entity.imageUrl ?: return
        val fileId = "cover_" + extractFileId(url)
        val targetFile = File(musicDir, fileId)

        if (targetFile.exists() && entity.md5Hash != null) {
            val actualHash = Utils.calculateMd5(targetFile)
            if (actualHash == entity.md5Hash) {
                albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
                return
            }
        }

        val provider = repository.getProvider("google_drive") ?: throw Exception("Provider not found")

        albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, 0)

        val dummySong = md.oak.sonark.data.model.Song(
            id = "cover_${entity.id}",
            title = "Cover",
            artist = entity.artist,
            album = entity.title,
            duration = 0
        )
        val syncSong = md.oak.sonark.data.model.SyncSong(
            song = dummySong,
            data = url,
            albumId = entity.id,
            size = entity.size,
            md5Hash = entity.md5Hash,
            providerId = "google_drive"
        )

        val success = performDownload(syncSong, targetFile) { progress ->
            scope.launch {
                albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, progress)
            }
        }

        if (success) {
            val actualHash = Utils.calculateMd5(targetFile)
            if (entity.md5Hash == null || actualHash == entity.md5Hash) {
                albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
            } else {
                throw Exception("MD5 mismatch")
            }
        } else {
            throw Exception("Download failed")
        }
    }

    private suspend fun performDownload(
        syncSong: md.oak.sonark.data.model.SyncSong,
        targetFile: File,
        onProgressUpdate: (Int) -> Unit
    ): Boolean {
        val provider = repository.getProvider(syncSong.providerId) ?: return false
        var lastProgressUpdate = 0L
        return provider.downloadSong(syncSong, targetFile) { downloaded, total ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 500) {
                val progress = if (total > 0) {
                    (downloaded * 100 / total).toInt()
                } else 0
                onProgressUpdate(progress)
                lastProgressUpdate = currentTime
            }
        }
    }

    private fun extractFileId(dataUrl: String): String {
        return dataUrl.substringAfter("files/").substringBefore("?")
            .replace("/", "_")
            .replace(":", "_")
    }
}
