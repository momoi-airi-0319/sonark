package md.oak.sonark.data.repository

import kotlinx.coroutines.flow.*
import md.oak.sonark.data.model.*
import uniffi.sonark_sdk.*
import uniffi.sonark_sdk.Song as RustSong
import md.oak.sonark.data.model.Song as KotlinSong
import md.oak.sonark.data.model.DownloadStatus as KotlinStatus
import md.oak.sonark.data.model.Album as KotlinAlbum

class MusicRepository(
    private val engine: SonarkEngine
) {
    private val _songsFlow = MutableStateFlow<List<SyncSong>>(emptyList())
    val songsFlow: StateFlow<List<SyncSong>> = _songsFlow.asStateFlow()

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

    fun getAlbumsFlow(): Flow<List<KotlinAlbum>> = songsFlow.map { syncSongs ->
        syncSongs.groupBy { it.albumId }.map { (_, songs) ->
            val first = songs.first()
            val albumSongs = songs.map { it.song }
            if (songs.any { it.song.type == AlbumType.CUE }) {
                KotlinAlbum.Cue(
                    title = first.song.album,
                    artist = first.song.artist,
                    imageUrl = first.song.imageUrl,
                    localPath = first.localPath,
                    songs = albumSongs
                )
            } else {
                KotlinAlbum.Normal(
                    title = first.song.album,
                    artist = first.song.artist,
                    imageUrl = first.song.imageUrl,
                    localPath = first.localPath,
                    songs = albumSongs
                )
            }
        }
    }

    private fun refreshLocalCache() {
        val rustSongs = engine.getAllSongs()
        _songsFlow.value = rustSongs.map { it.toSyncSong() }
    }

    fun syncAll() {
        engine.syncLibrary()
    }

    private fun onSyncComplete(rustSongs: List<RustSong>) {
        _songsFlow.value = rustSongs.map { it.toSyncSong() }
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
        engine.startDownload(songId, song.data, song.localPath ?: "")
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
