package md.oak.sonark.data.download

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import md.oak.sonark.data.Utils
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.repository.MusicRepository
import java.io.File

class DownloadManager(
    private val context: Context,
    private val songDao: SongDao,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music").apply { if (!exists()) mkdirs() }
    
    private val activeDownloads = mutableSetOf<String>()
    private val downloadSemaphore = Semaphore(3)

    fun start() {
        scope.launch {
            songDao.getSongsToDownloadFlow().collect { entities ->
                val uniqueFiles = entities
                    .filter { it.downloadStatus == DownloadStatus.PENDING }
                    .groupBy { it.data }
                    .values
                    .map { it.first() }
                    .sortedBy { it.size }

                uniqueFiles.forEach { entity ->
                    if (!activeDownloads.contains(entity.data)) {
                        scope.launch {
                            downloadSemaphore.withPermit {
                                download(entity)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun download(entity: SongEntity) {
        if (!activeDownloads.add(entity.data)) return
        
        try {
            val fileId = extractFileId(entity.data)
            val targetFile = File(musicDir, fileId)
            
            if (targetFile.exists() && entity.md5Hash != null) {
                songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, 0)
                val actualHash = Utils.calculateMd5(targetFile)
                if (actualHash == entity.md5Hash) {
                    songDao.markUrlAsDownloaded(entity.data, targetFile.absolutePath)
                    return
                }
            }
            
            val provider = repository.getProvider(entity.providerId) ?: return
            
            songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, 0)
            
            // For download purposes, the metadata in SyncSong isn't strictly necessary as long as 'data' and 'size' are correct.
            val syncSong = entity.toSyncSong("Album", null, AlbumType.NORMAL)
            
            var lastProgressUpdate = 0L
            val success = provider.downloadSong(syncSong, targetFile) { downloaded, total ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressUpdate > 500) { // Throttle DB updates to 500ms
                    val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    scope.launch {
                        songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.DOWNLOADING, progress)
                    }
                    lastProgressUpdate = currentTime
                }
            }

            if (success) {
                val actualHash = Utils.calculateMd5(targetFile)
                if (entity.md5Hash == null || actualHash == entity.md5Hash) {
                    songDao.markUrlAsDownloaded(entity.data, targetFile.absolutePath)
                } else {
                    songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0)
                }
            } else {
                songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            songDao.updateDownloadStatusByUrl(entity.data, DownloadStatus.ERROR, 0)
        } finally {
            activeDownloads.remove(entity.data)
        }
    }

    private fun extractFileId(dataUrl: String): String {
        return dataUrl.substringAfter("files/").substringBefore("?")
            .replace("/", "_")
            .replace(":", "_")
    }
}
