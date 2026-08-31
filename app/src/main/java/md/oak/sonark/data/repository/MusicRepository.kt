package md.oak.sonark.data.repository

import kotlinx.coroutines.flow.*
import md.oak.sonark.data.model.*
import uniffi.sonark_sdk.*
import uniffi.sonark_sdk.Song as RustSong
import md.oak.sonark.data.model.Song as KotlinSong
import md.oak.sonark.data.model.DownloadStatus as KotlinStatus
import md.oak.sonark.data.model.Album as KotlinAlbum
import md.oak.sonark.data.model.Artist as KotlinArtist

import md.oak.sonark.data.Dependencies
import java.io.File

class MusicRepository(
    private val engine: SonarkEngine,
) {
    private val _songsFlow = MutableStateFlow<List<SyncSong>>(emptyList())
    val songsFlow: StateFlow<List<SyncSong>> = _songsFlow.asStateFlow()

    private val _albumsFlow = MutableStateFlow<List<KotlinAlbum>>(emptyList())
    val albumsFlow: StateFlow<List<KotlinAlbum>> = _albumsFlow.asStateFlow()

    private val _artistsFlow = MutableStateFlow<List<KotlinArtist>>(emptyList())
    val artistsFlow: StateFlow<List<KotlinArtist>> = _artistsFlow.asStateFlow()

    init {
        refreshLocalCache()
        engine.setObserver(object : SonarkObserver {
            override fun onDownloadProgress(progress: DownloadProgress) {
                this@MusicRepository.onDownloadProgress(progress)
            }

            override fun onSyncComplete(songs: List<RustSong>) {
                this@MusicRepository.onSyncComplete(songs)
            }

            override fun onError(message: String) {
                // Handle error
            }
        })
    }

    fun getSyncSongsFlow(): Flow<List<SyncSong>> = songsFlow
    fun getAlbumsFlow(): Flow<List<KotlinAlbum>> = albumsFlow
    fun getArtistsFlow(): Flow<List<KotlinArtist>> = artistsFlow

    private fun refreshLocalCache() {
        val rustSongs = engine.getAllSongs()
        _songsFlow.value = rustSongs.map { it.toSyncSong() }

        val rustAlbums = engine.getAllAlbums()
        _albumsFlow.value = rustAlbums.map { it.toKotlinAlbum() }

        val rustArtists = engine.getAllArtists()
        _artistsFlow.value = rustArtists.map {
            KotlinArtist(it.name, it.albumCount.toInt(), it.songCount.toInt())
        }
    }

    fun syncAll() {
        engine.syncLibrary()
    }

    private fun onSyncComplete(rustSongs: List<RustSong>) {
        refreshLocalCache()
    }

    private fun onDownloadProgress(progress: DownloadProgress) {
        _songsFlow.update { current ->
            current.map { 
                if (it.song.id == progress.songId) {
                    it.copy(
                        downloadStatus = KotlinStatus.DOWNLOADING,
                        downloadedBytes = progress.downloadedBytes.toLong(),
                        downloadProgress = if (progress.totalBytes > 0uL) (progress.downloadedBytes * 100uL / progress.totalBytes).toInt() else 0
                    )
                } else it
            }
        }
    }

    fun resumeDownload(songId: String) {
        val song = _songsFlow.value.find { it.song.id == songId } ?: return
        val destination = song.localPath ?: run {
            // Generate a default path if missing
            val musicDir = Dependencies.context.getExternalFilesDir("music") ?: return
            val fileName = "${song.song.artist} - ${song.song.title}".replace("/", "_") + ".mp3"
            File(musicDir, fileName).absolutePath
        }
        engine.startDownload(songId, song.data, destination)
    }

    fun pauseDownload(songId: String) {}
    fun pauseAlbumDownload(albumId: String) {}

    fun resumeAlbumDownload(albumId: String) {
        _songsFlow.value.filter { it.albumId == albumId && it.downloadStatus != KotlinStatus.COMPLETED }
            .forEach { resumeDownload(it.song.id) }
    }

    fun pauseAllDownloads() {}
    fun resumeAllDownloads() {}

    fun fetchMetadata(songId: String) {
        val song = _songsFlow.value.find { it.song.id == songId } ?: return
        song.localPath?.let { path ->
            engine.scanLocalMetadata(songId, path)?.let {
                refreshLocalCache()
            }
        }
    }

    fun searchSongs(query: String): List<SyncSong> {
        return engine.search(query).map { it.toSyncSong() }
    }

    fun getSongsForAlbum(albumId: String): List<SyncSong> {
        return engine.getSongsForAlbum(albumId).map { it.toSyncSong() }
    }

    fun getSongsForArtist(artistName: String): List<SyncSong> {
        return engine.getSongsForArtist(artistName).map { it.toSyncSong() }
    }

    fun getLibraryStats(): uniffi.sonark_sdk.LibraryStats {
        return engine.getLibraryStats()
    }

    fun getProvider(id: String): Any? = null 

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
