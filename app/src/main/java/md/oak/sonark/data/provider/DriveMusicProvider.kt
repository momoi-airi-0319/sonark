package md.oak.sonark.data.provider

import android.net.Uri
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.oak.sonark.data.model.Song
import java.io.FileOutputStream
import java.io.File as JavaFile

class DriveMusicProvider : MusicProvider {
    override val id: String = "google_drive"
    override val name: String = "Google Drive"

    private var driveService: Drive? = null
    var credential: GoogleAccountCredential? = null
        set(value) {
            field = value
            updateService()
        }

    private fun updateService() {
        val cred = credential
        driveService = if (cred != null) {
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                cred
            ).setApplicationName("Sonark").build()
        } else {
            null
        }
    }

    override suspend fun syncLibrary(): List<Song> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        val songs = mutableListOf<Song>()
        try {
            val vaultFolder = findFolder(service, "Vault", "root") ?: return@withContext emptyList()
            val albumFolders = listFolders(service, vaultFolder.id)

            for (albumFolder in albumFolders) {
                val files = listFiles(service, albumFolder.id)
                val coverFile = files.find { file ->
                    val name = file.name.lowercase()
                    name.startsWith("cover.") || name.startsWith("folder.")
                }
                val imageUrl = coverFile?.let { "https://www.googleapis.com/drive/v3/files/${it.id}?alt=media" }

                val cueFile = files.find { it.name.endsWith(".cue", ignoreCase = true) }
                if (cueFile != null) {
                    songs.addAll(parseCueAlbum(service, cueFile, files, albumFolder.name, imageUrl))
                } else {
                    for (file in files) {
                        if (isAudioFile(file)) {
                            songs.add(parseSong(file, albumFolder.name, imageUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songs
    }

    override suspend fun resolveStreamUri(song: Song): Uri {
        return Uri.parse(song.data)
    }

    override suspend fun downloadSong(song: Song, targetFile: JavaFile): Boolean = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext false
        val fileId = song.data.substringAfter("files/").substringBefore("?")
        try {
            FileOutputStream(targetFile).use { outputStream ->
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun getAuthHeaders(): Map<String, String> {
        val token = try {
            credential?.getToken()
        } catch (e: Exception) {
            null
        }
        return if (token != null) {
            mapOf("Authorization" to "Bearer $token")
        } else {
            emptyMap()
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
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".cue")
    }

    private suspend fun parseCueAlbum(
        service: Drive,
        cueFile: File,
        allFiles: List<File>,
        albumName: String,
        imageUrl: String?
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            val content = service.files().get(cueFile.id).executeMediaAsInputStream().bufferedReader().use { it.readText() }
            var currentAudioFile: File? = null
            var albumArtist = "Unknown Artist"
            var currentTrackTitle = ""
            var currentTrackArtist = ""
            var currentTrackNumber = ""
            
            content.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("FILE") -> {
                        val fileName = trimmed.substringAfter("\"").substringBeforeLast("\"")
                        currentAudioFile = allFiles.find { it.name.equals(fileName, ignoreCase = true) }
                    }
                    trimmed.startsWith("PERFORMER") && currentTrackNumber.isEmpty() -> {
                        albumArtist = trimmed.substringAfter("\"").substringBeforeLast("\"")
                    }
                    trimmed.startsWith("TRACK") -> {
                        currentTrackNumber = trimmed.substringAfter("TRACK ").substringBefore(" ").trim()
                        currentTrackArtist = albumArtist
                    }
                    trimmed.startsWith("TITLE") -> {
                        if (currentTrackNumber.isNotEmpty()) {
                            currentTrackTitle = trimmed.substringAfter("\"").substringBeforeLast("\"")
                        }
                    }
                    trimmed.startsWith("PERFORMER") && currentTrackNumber.isNotEmpty() -> {
                        currentTrackArtist = trimmed.substringAfter("\"").substringBeforeLast("\"")
                    }
                    trimmed.startsWith("INDEX 01") -> {
                        val timeStr = trimmed.substringAfter("INDEX 01 ").trim()
                        val offsetMs = parseCueTime(timeStr)
                        val audioFile = currentAudioFile ?: return@forEach
                        
                        songs.add(Song(
                            id = "${cueFile.id}_$currentTrackNumber",
                            title = currentTrackTitle,
                            artist = currentTrackArtist,
                            album = albumName,
                            duration = 0,
                            data = "https://www.googleapis.com/drive/v3/files/${audioFile.id}?alt=media",
                            albumId = cueFile.id,
                            imageUrl = imageUrl,
                            localPath = null,
                            isCueAlbum = true,
                            startOffset = offsetMs,
                            providerId = id
                        ))
                        
                        currentTrackTitle = ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songs
    }

    private fun parseCueTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        if (parts.size != 3) return 0L
        val minutes = parts[0].toLongOrNull() ?: 0L
        val seconds = parts[1].toLongOrNull() ?: 0L
        val frames = parts[2].toLongOrNull() ?: 0L
        return (minutes * 60 * 1000) + (seconds * 1000) + (frames * 1000 / 75)
    }

    private fun parseSong(file: File, albumName: String, imageUrl: String?): Song {
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
            localPath = null,
            providerId = id
        )
    }
}
