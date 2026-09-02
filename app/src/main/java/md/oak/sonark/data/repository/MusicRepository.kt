package md.oak.sonark.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import md.oak.sonark.data.model.*
import uniffi.sonark_sdk.*
import uniffi.sonark_sdk.Song as RustSong
import md.oak.sonark.data.model.Song as KotlinSong
import md.oak.sonark.data.model.DownloadStatus as KotlinStatus
import md.oak.sonark.data.model.Album as KotlinAlbum
import md.oak.sonark.data.model.Artist as KotlinArtist

import java.io.File
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
    }

    // Constructor overload for backward compatibility/testing with a fixed engine
    constructor(
        engine: SonarkEngineInterface,
        settingsRepository: SettingsRepository,
    ) : this(
        settingsRepository = settingsRepository,
        engineFactory = { engine }
    )

    @Synchronized
    private fun switchEngine(dbFile: File) {
        val path = dbFile.absolutePath
        android.util.Log.d("MusicRepository", "Switching to database: $path")
        val engine = engineCache.getOrPut(path) {
            engineFactory(dbFile)
        }
        currentEngine = engine
        try {
            engine.setObserver(observer)
        } catch (e: Exception) {
            android.util.Log.e("SonarkSDK", "Failed to set observer on engine", e)
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
            android.util.Log.e("SonarkSDK", "Failed to refresh local cache", e)
        }
    }

    fun syncAll() {
        val engine = currentEngine ?: return
        _syncStatus.value = SyncStatus.Syncing
        try {
            engine.syncLibrary()
        } catch (e: Exception) {
            android.util.Log.e("SonarkSDK", "Error starting syncLibrary", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed to start")
        }
    }

    private fun onSyncComplete(rustSongs: List<RustSong>) {
        refreshLocalCache()
        _syncStatus.value = SyncStatus.Success(rustSongs.size)
    }

    private fun onSyncError(message: String) {
        android.util.Log.e("SonarkSDK", "uniffi_sonark_sdk error: $message")
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

    fun resumeDownload(songId: String) {
        val engine = currentEngine ?: return
        val song = _songsFlow.value.find { it.song.id == songId } ?: return
        val destination = song.localPath ?: run {
            val musicDir = activeEmail?.let { settingsRepository.getAccountMusicDir(it) }
                ?: settingsRepository.getLegacyMusicDir()
                ?: return
            
            if (!musicDir.exists()) musicDir.mkdirs()
            val fileName = "${song.song.artist} - ${song.song.title}".replace("/", "_") + ".mp3"
            File(musicDir, fileName).absolutePath
        }
        engine.startDownload(songId, song.data, destination)
    }

    fun pauseDownload(@Suppress("UNUSED_PARAMETER") songId: String) {}
    fun pauseAlbumDownload(@Suppress("UNUSED_PARAMETER") albumId: String) {}

    fun resumeAlbumDownload(albumId: String) {
        _songsFlow.value.filter { it.albumId == albumId && it.downloadStatus != KotlinStatus.COMPLETED }
            .forEach { resumeDownload(it.song.id) }
    }

    fun pauseAllDownloads() {}
    fun resumeAllDownloads() {}

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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSongsForArtist(artistName: String): List<SyncSong> {
        val engine = currentEngine ?: return emptyList()
        return try {
            engine.getSongsForArtist(artistName).map { it.toSyncSong() }
        } catch (e: Exception) {
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

    private fun uniffi.sonark_sdk.Album.toKotlinAlbum(): KotlinAlbum {
        return KotlinAlbum.Normal(
            id = this.id,
            title = this.title,
            artist = this.artist,
            imageUrl = this.coverUrl,
            localPath = this.localCoverPath,
            songs = emptyList()
        )
    }

    private fun uniffi.sonark_sdk.DownloadStatus.toKotlin(): KotlinStatus {
        return when (this) {
            uniffi.sonark_sdk.DownloadStatus.NONE -> KotlinStatus.NONE
            uniffi.sonark_sdk.DownloadStatus.PENDING -> KotlinStatus.PENDING
            uniffi.sonark_sdk.DownloadStatus.DOWNLOADING -> KotlinStatus.DOWNLOADING
            uniffi.sonark_sdk.DownloadStatus.COMPLETED -> KotlinStatus.COMPLETED
            uniffi.sonark_sdk.DownloadStatus.PAUSED -> KotlinStatus.PAUSED
            uniffi.sonark_sdk.DownloadStatus.ERROR -> KotlinStatus.ERROR
        }
    }
}
