package md.oak.sonark.data.download

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import md.oak.sonark.data.Utils
import md.oak.sonark.data.database.AlbumDao
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.repository.MusicRepository
import java.io.File
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

class DownloadManager(
    context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val repository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music").apply { if (!exists()) mkdirs() }
    
    private val activeDownloads = Collections.synchronizedSet(mutableSetOf<String>())
    private val downloadSemaphore = Semaphore(6)

    fun start() {
        scope.launch {
            songDao.resetAllDownloadingStatus()
            albumDao.resetAllDownloadingStatus()
            observeSongDownloads()
            observeAlbumDownloads()
        }
    }

    private fun observeSongDownloads() = scope.launch {
        songDao.getSongsToDownloadFlow().collect { entities ->
            // Use a simple priority queue: smallest files first.
            // With correct file sizes, this naturally interleaves tracks from different albums.
            entities.asSequence()
                .sortedBy { it.size }
                .distinctBy { it.data }
                .forEach { entity ->
                    processDownload(entity.data) {
                        downloadSong(entity)
                    }
                }
        }
    }

    private fun observeAlbumDownloads() = scope.launch {
        albumDao.getAlbumsToDownloadFlow().collect { entities ->
            entities.forEach { entity ->
                val url = entity.imageUrl ?: return@forEach
                processDownload(url) {
                    downloadAlbumCover(entity)
                }
            }
        }
    }

    private fun processDownload(url: String, block: suspend () -> Unit) {
        if (!activeDownloads.add(url)) return
        
        scope.launch {
            var attempt = 0
            try {
                // Optimization: Check if already downloaded BEFORE taking a semaphore slot
                // This is implemented inside downloadSong/downloadAlbumCover, but we want to
                // exit early if possible. However, to keep it clean, we'll let the block 
                // handle the check but move the semaphore acquisition closer to the IO.
                
                while (true) {
                    try {
                        block()
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                        attempt++
                        val delayTime = (1000L * attempt * attempt).coerceAtMost(60_000L)
                        delay(delayTime.milliseconds)
                    }
                }
            } catch (_: CancellationException) {
                // Ignore
            } finally {
                activeDownloads.remove(url)
            }
        }
    }

    private suspend fun downloadSong(entity: md.oak.sonark.data.database.SongEntity) {
        val targetFile = File(musicDir, extractFileId(entity.data))
        
        if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
            markSongDownloaded(entity, targetFile)
            return
        }

        downloadSemaphore.withPermit {
            // Re-check after getting permit
            if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
                markSongDownloaded(entity, targetFile)
                return@withPermit
            }

            songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, 0)
            
            val syncSong = entity.toSyncSong(albumTitle = "Album", imageUrl = null, type = AlbumType.NORMAL)
            performValidatedDownload(syncSong, targetFile, entity.md5Hash) { progress ->
                songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, progress)
            }

            markSongDownloaded(entity, targetFile)
        }
    }

    private suspend fun markSongDownloaded(entity: md.oak.sonark.data.database.SongEntity, file: File) {
        songDao.markUrlAsDownloaded(entity.data, file.absolutePath)
        scope.launch { repository.fetchMetadata(entity.id) }
    }

    private suspend fun downloadAlbumCover(entity: md.oak.sonark.data.database.AlbumEntity) {
        val url = entity.imageUrl ?: return
        val targetFile = File(musicDir, "cover_" + extractFileId(url))

        if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
            albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
            return
        }

        downloadSemaphore.withPermit {
            if (isAlreadyDownloaded(targetFile, entity.md5Hash)) {
                albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
                return@withPermit
            }

            albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, 0)

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

            performValidatedDownload(syncSong, targetFile, entity.md5Hash) { progress ->
                albumDao.updateDownloadStatusByUrl(url, DownloadStatus.DOWNLOADING, progress)
            }

            albumDao.markUrlAsDownloaded(url, targetFile.absolutePath)
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
        onProgressUpdate: suspend (Int) -> Unit
    ) {
        val provider = repository.getProvider(syncSong.providerId) 
            ?: throw IllegalStateException("Provider ${syncSong.providerId} not found")

        var lastProgressUpdate = 0L
        val success = provider.downloadSong(syncSong, targetFile) { downloaded, total ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdate > 500) {
                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                scope.launch { onProgressUpdate(progress) }
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
