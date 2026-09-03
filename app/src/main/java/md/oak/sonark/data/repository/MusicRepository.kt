package md.oak.sonark.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import md.oak.sonark.data.Dependencies
import md.oak.sonark.data.model.*
import uniffi.sonark_sdk.*
import uniffi.sonark_sdk.Song as RustSong
import md.oak.sonark.data.model.Song as KotlinSong
import md.oak.sonark.data.model.DownloadStatus as KotlinStatus
import md.oak.sonark.data.model.Album as KotlinAlbum
import md.oak.sonark.data.model.Artist as KotlinArtist

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val songCount: Int, val timestamp: Long = System.currentTimeMillis()) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class MusicRepository(
    private val settingsRepository: SettingsRepository,
    private val engineFactory: (File) -> SonarkEngineInterface,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val engineCache = ConcurrentHashMap<String, SonarkEngineInterface>()
    private var currentEngine: SonarkEngineInterface? = null
    private var activeEmail: String? = null

    private val _songsFlow = MutableStateFlow<List<SyncSong>>(emptyList())
    val songsFlow: StateFlow<List<SyncSong>> = _songsFlow.asStateFlow()

    private val _albumsFlow = MutableStateFlow<List<KotlinAlbum>>(emptyList())
    val albumsFlow: StateFlow<List<KotlinAlbum>> = _albumsFlow.asStateFlow()

    private val _artistsFlow = MutableStateFlow<List<KotlinArtist>>(emptyList())
    val artistsFlow: StateFlow<List<KotlinArtist>> = _artistsFlow.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val isSyncing: StateFlow<Boolean> = _syncStatus
        .map { it is SyncStatus.Syncing }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), initialValue = false)

    // Download Manager Fields
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private var isMeteredNetwork = false
    private var maxThreads = 6
    private var pauseOnMetered = true

    private val observer = object : SonarkObserver {
        override fun onDownloadProgress(progress: DownloadProgress) {
            this@MusicRepository.onDownloadProgress(progress)
        }

        override fun onSyncComplete(songs: List<RustSong>) {
            this@MusicRepository.onSyncComplete(songs)
        }

        override fun onError(message: String) {
            this@MusicRepository.onSyncError(message)
        }
    }

    init {
        // Track active account and switch account database dynamically
        repositoryScope.launch {
            settingsRepository.googleAccountName.collect { email ->
                activeEmail = email
                val dbFile = if (email != null) {
                    settingsRepository.getAccountDatabaseFile(email)
                } else {
                    settingsRepository.getLegacyDatabaseFile()
                }
                switchEngine(dbFile)
            }
        }

        // Track download settings
        repositoryScope.launch {
            settingsRepository.maxConcurrentDownloads.collect { threads ->
                maxThreads = threads
                scheduleDownloads()
            }
        }

        repositoryScope.launch {
            settingsRepository.pauseOnMeteredNetwork.collect { pause ->
                pauseOnMetered = pause
                scheduleDownloads()
            }
        }

        registerNetworkCallback()
    }

    // Constructor overload for backward compatibility/testing with a fixed engine
    constructor(
        engine: SonarkEngineInterface,
        settingsRepository: SettingsRepository,
    ) : this(
        settingsRepository = settingsRepository,
        engineFactory = { engine }
    )

    private fun registerNetworkCallback() {
        try {
            val context = Dependencies.context
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            
            updateNetworkMeteredState()
            
            val request = NetworkRequest.Builder().build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val metered = cm.isActiveNetworkMetered
                    if (metered != isMeteredNetwork) {
                        isMeteredNetwork = metered
                        scheduleDownloads()
                    }
                }
                override fun onLost(network: Network) {
                    updateNetworkMeteredState()
                    scheduleDownloads()
                }
            })
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to register network callback", e)
        }
    }

    private fun updateNetworkMeteredState() {
        try {
            val context = Dependencies.context
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            isMeteredNetwork = cm?.isActiveNetworkMetered == true
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun switchEngine(dbFile: File) {
        val path = dbFile.absolutePath
        Log.d("MusicRepository", "Switching to database: $path")
        val engine = engineCache.getOrPut(path) {
            engineFactory(dbFile)
        }
        currentEngine = engine
        try {
            engine.setObserver(observer)
        } catch (e: Exception) {
            Log.e("SonarkSDK", "Failed to set observer on engine", e)
        }
        refreshLocalCache()
    }

    fun getSyncSongsFlow(): Flow<List<SyncSong>> = songsFlow
    fun getAlbumsFlow(): Flow<List<KotlinAlbum>> = albumsFlow
    fun getArtistsFlow(): Flow<List<KotlinArtist>> = artistsFlow

    fun refreshLocalCache() {
        val engine = currentEngine ?: return
        try {
            val rustSongs = engine.getAllSongs()
            _songsFlow.value = rustSongs.map { it.toSyncSong() }

            val rustAlbums = engine.getAllAlbums()
            _albumsFlow.value = rustAlbums.map { it.toKotlinAlbum() }

            val rustArtists = engine.getAllArtists()
            _artistsFlow.value = rustArtists.map {
                KotlinArtist(it.name, it.albumCount.toInt(), it.songCount.toInt())
            }
        } catch (e: Exception) {
            Log.e("SonarkSDK", "Failed to refresh local cache", e)
        }
    }

    fun syncAll() {
        val engine = currentEngine ?: return
        _syncStatus.value = SyncStatus.Syncing
        try {
            engine.syncLibrary()
        } catch (e: Exception) {
            Log.e("SonarkSDK", "Error starting syncLibrary", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed to start")
        }
    }

    private fun onSyncComplete(rustSongs: List<RustSong>) {
        refreshLocalCache()
        _syncStatus.value = SyncStatus.Success(rustSongs.size)
    }

    private fun onSyncError(message: String) {
        Log.e("SonarkSDK", "uniffi_sonark_sdk error: $message")
        _syncStatus.value = SyncStatus.Error(message)
    }

    private fun onDownloadProgress(progress: DownloadProgress) {
        _songsFlow.update { current ->
            current.map { 
                if (it.song.id == progress.songId) {
                    it.copy(
                        downloadStatus = KotlinStatus.DOWNLOADING,
                        downloadedBytes = progress.downloadedBytes.toLong(),
                        downloadProgress = if (progress.totalBytes > 0uL) ((progress.downloadedBytes * 100uL) / progress.totalBytes).toInt() else 0
                    )
                } else it
            }
        }
    }

    @Synchronized
    private fun scheduleDownloads() {
        updateNetworkMeteredState()
        val meteredPaused = isMeteredNetwork && pauseOnMetered
        
        var activeCount = 0
        val songsToStart = mutableListOf<SyncSong>()

        _songsFlow.update { list ->
            list.map { song ->
                if (song.downloadStatus == KotlinStatus.COMPLETED || song.downloadStatus == KotlinStatus.NONE) {
                    song
                } else {
                    if (song.isUserPaused) {
                        downloadJobs.remove(song.song.id)?.cancel()
                        song.copy(downloadStatus = KotlinStatus.PAUSED, pauseReason = PauseReason.USER_PAUSED)
                    } else if (meteredPaused) {
                        downloadJobs.remove(song.song.id)?.cancel()
                        song.copy(downloadStatus = KotlinStatus.PAUSED, pauseReason = PauseReason.METERED_NETWORK)
                    } else {
                        val isCurrentlyRunning = downloadJobs.containsKey(song.song.id) && downloadJobs[song.song.id]?.isActive == true
                        if (isCurrentlyRunning) {
                            activeCount++
                            song.copy(downloadStatus = KotlinStatus.DOWNLOADING, pauseReason = null)
                        } else if (activeCount < maxThreads) {
                            activeCount++
                            songsToStart.add(song)
                            song.copy(downloadStatus = KotlinStatus.DOWNLOADING, pauseReason = null)
                        } else {
                            song.copy(downloadStatus = KotlinStatus.PAUSED, pauseReason = PauseReason.THREAD_LIMIT)
                        }
                    }
                }
            }
        }

        songsToStart.forEach { song ->
            startDownloadJob(song)
        }
    }

    private fun getDestinationPath(song: SyncSong): String {
        return song.localPath ?: run {
            val musicDir = activeEmail?.let { settingsRepository.getAccountMusicDir(it) }
                ?: settingsRepository.getLegacyMusicDir()
                ?: File(Dependencies.context.filesDir, "music")
            
            if (!musicDir.exists()) musicDir.mkdirs()
            val fileName = "${song.song.artist} - ${song.song.title}".replace("/", "_") + ".mp3"
            File(musicDir, fileName).absolutePath
        }
    }

    private fun startDownloadJob(song: SyncSong) {
        if (downloadJobs.containsKey(song.song.id) && downloadJobs[song.song.id]?.isActive == true) return

        val job = repositoryScope.launch(Dispatchers.IO) {
            val destinationPath = getDestinationPath(song)
            val tmpFile = File("$destinationPath.tmp")
            val destFile = File(destinationPath)
            destFile.parentFile?.mkdirs()

            try {
                val token = Dependencies.authManager.getLastKnownToken()
                    ?: Dependencies.authManager.silentSignIn(
                        activeEmail ?: settingsRepository.googleAccountName.firstOrNull() ?: ""
                    )

                var existingLength = if (tmpFile.exists()) tmpFile.length() else 0L

                val urlConnection = URL(song.data).openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 15000
                urlConnection.readTimeout = 15000
                urlConnection.requestMethod = "GET"
                if (!token.isNullOrEmpty()) {
                    urlConnection.setRequestProperty("Authorization", "Bearer $token")
                }
                urlConnection.setRequestProperty("User-Agent", "Sonark")
                if (existingLength > 0) {
                    urlConnection.setRequestProperty("Range", "bytes=$existingLength-")
                }

                val responseCode = urlConnection.responseCode
                val isPartial = responseCode == 206
                val isOk = responseCode == 200

                if (!isPartial && !isOk) {
                    if (responseCode == 416 || responseCode == 401 || responseCode == 403) {
                        tmpFile.delete()
                        existingLength = 0L
                    } else {
                        throw IOException("HTTP error code: $responseCode")
                    }
                }

                if (!isPartial && existingLength > 0) {
                    tmpFile.delete()
                    existingLength = 0L
                }

                val contentLength = urlConnection.contentLengthLong
                val totalBytes = if (isPartial) existingLength + contentLength else if (contentLength > 0) contentLength else song.size

                val inputStream = urlConnection.inputStream
                val outputStream = FileOutputStream(tmpFile, isPartial && existingLength > 0)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = if (isPartial) existingLength else 0L
                var lastReportedBytes = downloadedBytes

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!coroutineContext.isActive) {
                        outputStream.close()
                        inputStream.close()
                        urlConnection.disconnect()
                        return@launch
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (downloadedBytes - lastReportedBytes > 100 * 1024 || downloadedBytes == totalBytes) {
                        lastReportedBytes = downloadedBytes
                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        _songsFlow.update { list ->
                            list.map {
                                if (it.song.id == song.song.id) {
                                    it.copy(
                                        downloadStatus = KotlinStatus.DOWNLOADING,
                                        downloadedBytes = downloadedBytes,
                                        downloadProgress = progress
                                    )
                                } else it
                            }
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                urlConnection.disconnect()

                if (tmpFile.exists()) {
                    tmpFile.renameTo(destFile)
                }

                val engine = currentEngine
                engine?.scanLocalMetadata(song.song.id, destinationPath)

                _songsFlow.update { list ->
                    list.map {
                        if (it.song.id == song.song.id) {
                            it.copy(
                                downloadStatus = KotlinStatus.COMPLETED,
                                localPath = destinationPath,
                                downloadProgress = 100,
                                downloadedBytes = destFile.length()
                            )
                        } else it
                    }
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("MusicRepository", "Download failed for ${song.song.title}", e)
                _songsFlow.update { list ->
                    list.map {
                        if (it.song.id == song.song.id) {
                            it.copy(
                                downloadStatus = KotlinStatus.ERROR,
                                errorMessage = e.message ?: "Download failed"
                            )
                        } else it
                    }
                }
            } finally {
                downloadJobs.remove(song.song.id)
                scheduleDownloads()
            }
        }

        downloadJobs[song.song.id] = job
    }

    fun resumeDownload(songId: String) {
        _songsFlow.update { list ->
            list.map {
                if (it.song.id == songId && it.downloadStatus != KotlinStatus.COMPLETED) {
                    it.copy(
                        isUserPaused = false,
                        downloadStatus = KotlinStatus.PENDING,
                        pauseReason = null
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun pauseDownload(songId: String) {
        _songsFlow.update { list ->
            list.map {
                if (it.song.id == songId) {
                    it.copy(
                        isUserPaused = true,
                        downloadStatus = KotlinStatus.PAUSED,
                        pauseReason = PauseReason.USER_PAUSED
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun pauseAlbumDownload(albumId: String) {
        _songsFlow.update { list ->
            list.map {
                if (it.albumId == albumId && it.downloadStatus != KotlinStatus.COMPLETED) {
                    it.copy(
                        isUserPaused = true,
                        downloadStatus = KotlinStatus.PAUSED,
                        pauseReason = PauseReason.USER_PAUSED
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun resumeAlbumDownload(albumId: String) {
        _songsFlow.update { list ->
            list.map {
                if (it.albumId == albumId && it.downloadStatus != KotlinStatus.COMPLETED) {
                    it.copy(
                        isUserPaused = false,
                        downloadStatus = KotlinStatus.PENDING,
                        pauseReason = null
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun pauseAllDownloads() {
        _songsFlow.update { list ->
            list.map {
                if (it.downloadStatus != KotlinStatus.COMPLETED && it.downloadStatus != KotlinStatus.NONE) {
                    it.copy(
                        isUserPaused = true,
                        downloadStatus = KotlinStatus.PAUSED,
                        pauseReason = PauseReason.USER_PAUSED
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun resumeAllDownloads() {
        _songsFlow.update { list ->
            list.map {
                if (it.downloadStatus != KotlinStatus.COMPLETED && it.downloadStatus != KotlinStatus.NONE) {
                    it.copy(
                        isUserPaused = false,
                        downloadStatus = KotlinStatus.PENDING,
                        pauseReason = null
                    )
                } else it
            }
        }
        scheduleDownloads()
    }

    fun fetchMetadata(songId: String) {
        val engine = currentEngine ?: return
        val song = _songsFlow.value.find { it.song.id == songId } ?: return
        song.localPath?.let { path ->
            engine.scanLocalMetadata(songId, path)?.let {
                refreshLocalCache()
            }
        }
    }

    fun searchSongs(query: String): List<SyncSong> {
        val engine = currentEngine ?: return emptyList()
        return try {
            engine.search(query).map { it.toSyncSong() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSongsForAlbum(albumId: String): List<SyncSong> {
        val engine = currentEngine ?: return emptyList()
        return try {
            engine.getSongsForAlbum(albumId).map { it.toSyncSong() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSongsForArtist(artistName: String): List<SyncSong> {
        val engine = currentEngine ?: return emptyList()
        return try {
            engine.getSongsForArtist(artistName).map { it.toSyncSong() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun RustSong.toSyncSong(): SyncSong {
        return SyncSong(
            song = KotlinSong(
                id = this.id,
                title = this.title,
                artist = this.artist,
                album = this.album,
                duration = this.durationMs.toLong(),
                imageUrl = this.coverUrl,
                trackNumber = this.trackNumber.toInt(),
                discNumber = this.discNumber.toInt(),
                type = if (this.isCue) AlbumType.CUE else AlbumType.NORMAL
            ),
            data = this.dataUrl,
            albumId = this.albumId,
            providerId = "rust_engine",
            size = this.size.toLong(),
            md5Hash = this.md5Hash,
            localPath = this.localPath,
            downloadStatus = this.downloadStatus.toKotlin(),
            startOffset = this.startOffsetMs.toLong()
        )
    }

    private fun Album.toKotlinAlbum(): KotlinAlbum {
        return KotlinAlbum.Normal(
            id = this.id,
            title = this.title,
            artist = this.artist,
            imageUrl = this.coverUrl,
            localPath = this.localCoverPath,
            songs = emptyList()
        )
    }

    private fun DownloadStatus.toKotlin(): KotlinStatus {
        return when (this) {
            DownloadStatus.NONE -> KotlinStatus.NONE
            DownloadStatus.PENDING -> KotlinStatus.PENDING
            DownloadStatus.DOWNLOADING -> KotlinStatus.DOWNLOADING
            DownloadStatus.COMPLETED -> KotlinStatus.COMPLETED
            DownloadStatus.PAUSED -> KotlinStatus.PAUSED
            DownloadStatus.ERROR -> KotlinStatus.ERROR
        }
    }
}
