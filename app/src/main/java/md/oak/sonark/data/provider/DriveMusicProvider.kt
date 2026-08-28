package md.oak.sonark.data.provider

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
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
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.io.File as JavaFile

class DriveMusicProvider : MusicProvider {
    override val id: String = "google_drive"
    override val name: String = "Google Drive"

    private companion object {
        const val TAG = "DriveMusicProvider"
        const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        const val MEDIA_URL_FORMAT = "https://www.googleapis.com/drive/v3/files/%s?alt=media"
        const val BUFFER_SIZE = 8192
        const val TIMEOUT_SECONDS = 30L
        const val MAX_REQUESTS_PER_HOST = 10
        const val HTTP_CODE_PARTIAL_CONTENT = 206
        const val HTTP_CODE_RANGE_NOT_SATISFIABLE = 416
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .dispatcher(
            Dispatcher().apply {
                maxRequestsPerHost = MAX_REQUESTS_PER_HOST
            },
        )
        .build()

    private var driveService: Drive? = null
    var credential: GoogleAccountCredential? = null
        set(value) {
            field = value
            updateService()
        }

    private fun updateService() {
        driveService = credential?.let { cred ->
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                cred
            ).setApplicationName("Sonark").build()
        }
    }

    override suspend fun syncLibrary(): List<SyncSong> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        val songs = mutableListOf<SyncSong>()

        try {
            val vaultFolder = findFolder(service, "Vault", "root") ?: return@withContext emptyList()
            val albumFolders = listFolders(service, vaultFolder.id)

            for (albumFolder in albumFolders) {
                processAlbumFolder(service, albumFolder, songs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync library", e)
        }
        songs
    }

    private suspend fun processAlbumFolder(service: Drive, albumFolder: File, songs: MutableList<SyncSong>) {
        val files = listFiles(service, albumFolder.id)
        val coverInfo = extractCoverInfo(files)
        val (folderArtist, folderAlbum) = parseFolderName(albumFolder.name)
        
        val cueFiles = files.asSequence()
            .filter { it.name.endsWith(".cue", ignoreCase = true) }
            .sortedBy { it.name }
            .toList()
        if (cueFiles.isNotEmpty()) {
            cueFiles.forEachIndexed { index, cueFile ->
                songs.addAll(parseCueAlbum(service, cueFile, files, folderAlbum, folderArtist, coverInfo, discNumber = index + 1))
            }
        } else {
            files.filter { isAudioFile(it) }.forEach { file ->
                songs.add(parseSong(file, albumFolder.id, folderAlbum, folderArtist, coverInfo))
            }
        }
    }

    private fun parseFolderName(folderName: String): Pair<String, String> {
        val parts = folderName.split(" - ", limit = 2)
        return if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            "Various Artists" to folderName.trim()
        }
    }

    private data class CoverInfo(val url: String?, val size: Long, val md5: String?)

    private fun extractCoverInfo(files: List<File>): CoverInfo {
        val coverFile = files.find { file ->
            val name = file.name.lowercase()
            name.startsWith("cover.") || name.startsWith("folder.")
        }
        return CoverInfo(
            url = coverFile?.id?.let { MEDIA_URL_FORMAT.format(it) },
            size = coverFile?.size?.toLong() ?: 0L,
            md5 = coverFile?.md5Checksum
        )
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
                handleDownloadResponse(response, song, targetFile, existingSize, onProgress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${song.song.title}", e)
            false
        }
    }

    private fun handleDownloadResponse(
        response: okhttp3.Response,
        song: SyncSong,
        targetFile: JavaFile,
        existingSize: Long,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        if (!response.isSuccessful && response.code != HTTP_CODE_RANGE_NOT_SATISFIABLE) {
            Log.e(TAG, "Download failed for ${song.song.title}: HTTP ${response.code}")
            return false
        }

        if (response.code == HTTP_CODE_RANGE_NOT_SATISFIABLE) return true

        val body = response.body ?: return false
        val append = response.code == HTTP_CODE_PARTIAL_CONTENT
        val totalSize = calculateTotalSize(response, body.contentLength(), song.size, append)

        return writeBodyToFile(body, targetFile, append, existingSize, totalSize, onProgress)
    }

    private fun calculateTotalSize(response: okhttp3.Response, contentLength: Long, songSize: Long, isAppend: Boolean): Long {
        return if (isAppend) {
            response.header("Content-Range")
                ?.substringAfterLast("/")
                ?.toLongOrNull() ?: songSize
        } else {
            if (contentLength > 0) contentLength else songSize
        }
    }

    private fun writeBodyToFile(
        body: ResponseBody,
        targetFile: JavaFile,
        append: Boolean,
        existingSize: Long,
        totalSize: Long,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        return try {
            FileOutputStream(targetFile, append).use { fos ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalDownloaded = if (append) existingSize else 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        onProgress(totalDownloaded, totalSize)
                    }
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error writing file ${targetFile.name}", e)
            false
        }
    }

    override fun getAuthHeaders(): Map<String, String> {
        val token = try {
            credential?.token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get token", e)
            null
        }
        return if (token != null) {
            mapOf("Authorization" to "Bearer $token")
        } else {
            emptyMap()
        }
    }

    private fun findFolder(service: Drive, name: String, parentId: String): File? {
        val query = "name = '$name' and mimeType = '$MIME_TYPE_FOLDER' and '$parentId' in parents and trashed = false"
        return service.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
            .files?.firstOrNull()
    }

    private fun listFolders(service: Drive, parentId: String): List<File> {
        val query = "'$parentId' in parents and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
        return service.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
            .files ?: emptyList()
    }

    private fun listFiles(service: Drive, parentId: String): List<File> {
        val query = "'$parentId' in parents and trashed = false"
        return service.files().list()
            .setQ(query)
            .setFields("files(id, name, mimeType, size, md5Checksum)")
            .execute()
            .files ?: emptyList()
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
        artistName: String,
        coverInfo: CoverInfo,
        discNumber: Int = 0
    ): List<SyncSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SyncSong>()
        try {
            val content = service.files().get(cueFile.id).executeMediaAsInputStream().bufferedReader().use { it.readText() }
            val parser = CueParser(content, allFiles, cueFile.id, albumName, artistName, coverInfo, discNumber, id)
            songs.addAll(parser.parse())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse CUE file ${cueFile.name}", e)
        }
        songs
    }

    private fun parseSong(file: File, albumId: String, albumName: String, artistName: String, coverInfo: CoverInfo): SyncSong {
        val (disc, track, title) = FilenameParser.parse(file.name)

        val song = Song(
            id = file.id,
            title = title,
            artist = artistName,
            album = albumName,
            duration = 0,
            discNumber = disc,
            trackNumber = track,
            imageUrl = coverInfo.url,
            type = AlbumType.NORMAL
        )

        return SyncSong(
            song = song,
            data = MEDIA_URL_FORMAT.format(file.id),
            albumId = albumId,
            providerId = id,
            size = (file.get("size") as? Number)?.toLong() ?: 0L,
            md5Hash = file.md5Checksum,
            coverData = coverInfo.url,
            coverSize = coverInfo.size,
            coverMd5 = coverInfo.md5
        )
    }

    private class CueParser(
        private val content: String,
        private val allFiles: List<File>,
        private val cueFileId: String,
        private val albumName: String,
        private val defaultArtist: String,
        private val coverInfo: CoverInfo,
        private val discNumber: Int,
        private val providerId: String
    ) {
        fun parse(): List<SyncSong> {
            val tempSongs = mutableListOf<SyncSong>()
            var currentAudioFile: File? = null
            var albumArtist = defaultArtist
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

                        val song = Song(
                            id = "${cueFileId}_${discNumber}_$currentTrackNumber",
                            title = currentTrackTitle,
                            artist = currentTrackArtist,
                            album = albumName,
                            duration = 0,
                            discNumber = discNumber,
                            trackNumber = currentTrackNumber.toIntOrNull() ?: 0,
                            imageUrl = coverInfo.url,
                            type = AlbumType.CUE
                        )

                        tempSongs.add(SyncSong(
                            song = song,
                            data = MEDIA_URL_FORMAT.format(audioFile.id),
                            albumId = cueFileId,
                            providerId = providerId,
                            size = (audioFile.get("size") as? Number)?.toLong() ?: 0L,
                            md5Hash = audioFile.md5Checksum,
                            startOffset = offsetMs,
                            coverData = coverInfo.url,
                            coverSize = coverInfo.size,
                            coverMd5 = coverInfo.md5
                        ))

                        currentTrackTitle = ""
                    }
                }
            }

            return calculateDurations(tempSongs)
        }

        private fun calculateDurations(tempSongs: List<SyncSong>): List<SyncSong> {
            return tempSongs.mapIndexed { i, current ->
                val duration = if (i < tempSongs.size - 1) {
                    tempSongs[i + 1].startOffset - current.startOffset
                } else {
                    0L
                }
                current.copy(song = current.song.copy(duration = duration))
            }
        }

        private fun parseCueTime(timeStr: String): Long {
            val parts = timeStr.split(":")
            if (parts.size != 3) return 0L
            val minutes = parts[0].toLongOrNull() ?: 0L
            val seconds = parts[1].toLongOrNull() ?: 0L
            val frames = parts[2].toLongOrNull() ?: 0L
            return (minutes * 60 * 1000) + (seconds * 1000) + (frames * 1000 / 75)
        }
    }

    private object FilenameParser {
        private val multiDiscRegex = """^(\d+)-(\d+)\s*[-.]?\s*(.*)$""".toRegex()
        private val singleDiscRegex = """^(\d+)\s*[-.]?\s*(.*)$""".toRegex()

        fun parse(filename: String): Triple<Int, Int, String> {
            val cleanName = filename.substringBeforeLast(".")
            multiDiscRegex.find(cleanName)?.let { match ->
                return Triple(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].trim())
            }
            singleDiscRegex.find(cleanName)?.let { match ->
                return Triple(0, match.groupValues[1].toInt(), match.groupValues[2].trim())
            }
            return Triple(0, 0, cleanName)
        }
    }
}
