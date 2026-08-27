package md.oak.sonark.data.provider

import android.net.Uri
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.File as JavaFile
import androidx.core.net.toUri

class DriveMusicProvider : MusicProvider {
    override val id: String = "google_drive"
    override val name: String = "Google Drive"

    private val httpClient = OkHttpClient()
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

    override suspend fun syncLibrary(): List<SyncSong> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        val songs = mutableListOf<SyncSong>()
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
                val coverSize = coverFile?.size?.toLong() ?: 0L
                val coverMd5 = coverFile?.md5Checksum

                val cueFiles = files.filter { it.name.endsWith(".cue", ignoreCase = true) }.sortedBy { it.name }
                if (cueFiles.isNotEmpty()) {
                    cueFiles.forEachIndexed { index, cueFile ->
                        songs.addAll(parseCueAlbum(service, cueFile, files, albumFolder.name, imageUrl, coverSize, coverMd5, discNumber = index + 1))
                    }
                } else {
                    for (file in files) {
                        if (isAudioFile(file)) {
                            songs.add(parseSong(file, albumFolder.id, albumFolder.name, imageUrl, coverSize, coverMd5))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songs
    }

    override suspend fun resolveStreamUri(song: SyncSong): Uri {
        return song.data.toUri()
    }

    override suspend fun downloadSong(
        song: SyncSong, 
        targetFile: JavaFile,
        onProgress: (Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders()
        val existingSize = if (targetFile.exists()) targetFile.length() else 0L
        
        if (song.size in 1..existingSize) return@withContext true

        val request = Request.Builder()
            .url(song.data)
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
                if (existingSize > 0) {
                    addHeader("Range", "bytes=$existingSize-")
                }
            }
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 416) return@withContext false
                
                if (response.code == 416) return@withContext true 

                val body = response.body ?: return@withContext false
                val append = response.code == 206
                
                FileOutputStream(targetFile, append).use { fos ->
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = if (append) existingSize else 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        onProgress(totalDownloaded, song.size)
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun getAuthHeaders(): Map<String, String> {
        val token = try {
            credential?.getToken()
        } catch (_: Exception) {
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
            .setFields("files(id, name, mimeType, size, md5Checksum)")
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
        imageUrl: String?,
        coverSize: Long = 0,
        coverMd5: String? = null,
        discNumber: Int = 0
    ): List<SyncSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SyncSong>()
        try {
            val content = service.files().get(cueFile.id).executeMediaAsInputStream().bufferedReader().use { it.readText() }
            var currentAudioFile: File? = null
            var albumArtist = "Unknown Artist"
            var currentTrackTitle = ""
            var currentTrackArtist = ""
            var currentTrackNumber = ""
            
            val tempSongs = mutableListOf<SyncSong>()
            
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
                        
                        val song = Song(
                            id = "${cueFile.id}_${discNumber}_$currentTrackNumber",
                            title = currentTrackTitle,
                            artist = currentTrackArtist,
                            album = albumName,
                            duration = 0,
                            discNumber = discNumber,
                            trackNumber = currentTrackNumber.toIntOrNull() ?: 0,
                            imageUrl = imageUrl,
                            type = AlbumType.CUE
                        )
                        
                        tempSongs.add(SyncSong(
                            song = song,
                            data = "https://www.googleapis.com/drive/v3/files/${audioFile.id}?alt=media",
                            albumId = cueFile.id,
                            providerId = id,
                            size = audioFile.size.toLong(),
                            md5Hash = audioFile.md5Checksum,
                            startOffset = offsetMs,
                            coverData = imageUrl,
                            coverSize = coverSize,
                            coverMd5 = coverMd5
                        ))
                        
                        currentTrackTitle = ""
                    }
                }
            }

            // Calculate durations
            for (i in tempSongs.indices) {
                val current = tempSongs[i]
                val duration = if (i < tempSongs.size - 1) {
                    tempSongs[i + 1].startOffset - current.startOffset
                } else {
                    0L // We don't know the end of the file here easily, but better than nothing
                }
                songs.add(current.copy(song = current.song.copy(duration = duration)))
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

    private fun parseSong(file: File, albumId: String, albumName: String, imageUrl: String?, coverSize: Long = 0, coverMd5: String? = null): SyncSong {
        val (disc, track, title) = parseFilename(file.name)

        val song = Song(
            id = file.id,
            title = title,
            artist = "Unknown Artist",
            album = albumName,
            duration = 0,
            discNumber = disc,
            trackNumber = track,
            imageUrl = imageUrl,
            type = AlbumType.NORMAL
        )

        return SyncSong(
            song = song,
            data = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
            albumId = albumId,
            providerId = id,
            size = file.size.toLong(),
            md5Hash = file.md5Checksum,
            coverData = imageUrl,
            coverSize = coverSize,
            coverMd5 = coverMd5
        )
    }

    private fun parseFilename(filename: String): Triple<Int, Int, String> {
        val cleanName = filename.substringBeforeLast(".")
        // Match "1-01 - Title" or "1-01. Title" or "1-01 Title"
        val multiDiscRegex = """^(\d+)-(\d+)\s*[-.]?\s*(.*)$""".toRegex()
        // Match "01 - Title" or "01. Title" or "01 Title"
        val singleDiscRegex = """^(\d+)\s*[-.]?\s*(.*)$""".toRegex()

        multiDiscRegex.find(cleanName)?.let { match ->
            return Triple(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].trim())
        }
        singleDiscRegex.find(cleanName)?.let { match ->
            return Triple(0, match.groupValues[1].toInt(), match.groupValues[2].trim())
        }
        return Triple(0, 0, cleanName)
    }
}
