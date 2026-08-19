package md.oak.sonark.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.Song
import java.io.FileOutputStream

class MusicRepository(context: Context, private val songDao: SongDao) {

    private var driveService: Drive? = null
    private val cacheDir = java.io.File(context.cacheDir, "music_cache").apply { if (!exists()) mkdirs() }

    fun setDriveService(service: Drive?) {
        this.driveService = service
    }

    fun isServiceSet(): Boolean = driveService != null

    fun getSongsFlow(): Flow<List<Song>> {
        return songDao.getAllSongsFlow().map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun syncWithDrive() = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext
        try {
            val vaultFolder = findFolder(service, "Vault", "root") ?: return@withContext
            val albumFolders = listFolders(service, vaultFolder.id)
            val driveSongs = mutableListOf<Song>()

            for (albumFolder in albumFolders) {
                val files = listFiles(service, albumFolder.id)
                val coverFile = files.find { it.name.equals("cover.jpg", ignoreCase = true) }
                val imageUrl = coverFile?.let { "https://www.googleapis.com/drive/v3/files/${it.id}?alt=media" }

                for (file in files) {
                    if (isAudioFile(file)) {
                        driveSongs.add(parseSong(file, albumFolder.name, imageUrl))
                    }
                }
            }

            if (driveSongs.isNotEmpty()) {
                // Sync with database
                val entities = driveSongs.map { SongEntity.fromSong(it) }
                songDao.insertSongs(entities)
                songDao.deleteSongsNotIn(entities.map { it.id })
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findFolder(service: Drive, name: String, parentId: String): File? {
        val query = "name = '$name' and mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
        val result = service.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
        return result.files.firstOrNull()
    }

    private fun listFolders(service: Drive, parentId: String): List<File> {
        val query = "'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = service.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
        return result.files ?: emptyList()
    }

    private fun listFiles(service: Drive, parentId: String): List<File> {
        val query = "'$parentId' in parents and trashed = false"
        val result = service.files().list()
            .setQ(query)
            .setFields("files(id, name, mimeType, size)")
            .execute()
        return result.files ?: emptyList()
    }

    private fun isAudioFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a")
    }

    private fun parseSong(file: File, albumName: String, imageUrl: String?): Song {
        // Filename format: "01 - Song Title.mp3"
        val fileName = file.name.substringBeforeLast(".")
        val title = if (fileName.contains(" - ")) {
            fileName.substringAfter(" - ").trim()
        } else {
            fileName
        }

        return Song(
            id = file.id,
            title = title,
            artist = "Unknown Artist",
            album = albumName,
            duration = 0,
            data = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
            albumId = file.id,
            imageUrl = imageUrl,
            localPath = getLocalPath(file.id)
        )
    }

    private fun getLocalPath(fileId: String): String? {
        val file = java.io.File(cacheDir, fileId)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun downloadSong(song: Song): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        val localFile = java.io.File(cacheDir, song.id)
        if (localFile.exists()) return@withContext localFile.absolutePath

        try {
            val outputStream = FileOutputStream(localFile)
            service.files().get(song.id).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            val path = localFile.absolutePath
            
            // Update database
            songDao.getSongById(song.id)?.let { entity ->
                songDao.updateSong(entity.copy(localPath = path))
            }
            
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchMetadata(songId: String) = withContext(Dispatchers.IO) {
        val entity = songDao.getSongById(songId) ?: return@withContext
        if (entity.localPath == null) return@withContext
        
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entity.localPath)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entity.title
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            var imageUrl = entity.imageUrl
            if (imageUrl == null || imageUrl.startsWith("https://")) {
                val art = retriever.embeddedPicture
                if (art != null) {
                    val artFile = java.io.File(cacheDir, "art_${entity.id}.jpg")
                    if (!artFile.exists()) {
                        FileOutputStream(artFile).use { it.write(art) }
                    }
                    imageUrl = artFile.absolutePath
                }
            }
            
            songDao.updateSong(entity.copy(
                artist = artist, 
                title = title, 
                duration = duration, 
                imageUrl = imageUrl
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
    }
}
