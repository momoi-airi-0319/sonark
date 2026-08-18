package md.oak.sonark.data.repository

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.oak.sonark.data.model.Song

class MusicRepository {

    private var driveService: Drive? = null

    fun setDriveService(service: Drive?) {
        this.driveService = service
    }

    fun isServiceSet(): Boolean = driveService != null

    suspend fun getDriveSongs(): List<Song> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        val songs = mutableListOf<Song>()

        try {
            // 1. Find the "Vault" folder
            val vaultFolder = findFolder(service, "Vault", "root") ?: return@withContext emptyList()

            // 2. List album folders in "Vault"
            val albumFolders = listFolders(service, vaultFolder.id)

            for (albumFolder in albumFolders) {
                // 3. List files in album folder
                val files = listFiles(service, albumFolder.id)
                
                // Find cover.jpg if present
                val coverFile = files.find { it.name.equals("cover.jpg", ignoreCase = true) }
                val imageUrl = coverFile?.let { "https://drive.google.com/thumbnail?id=${it.id}&sz=w500" }

                for (file in files) {
                    if (isAudioFile(file)) {
                        songs.add(parseSong(file, albumFolder.name, imageUrl))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songs
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
            artist = "Unknown Artist", // Could be parsed if format was "Artist - Title"
            album = albumName,
            duration = 0, // Drive API doesn't easily give duration for all audio files without extra metadata
            data = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media", // API download link
            albumId = file.id, // Using file ID as albumId for simplicity or we could use albumFolder.id
            imageUrl = imageUrl
        )
    }
}
