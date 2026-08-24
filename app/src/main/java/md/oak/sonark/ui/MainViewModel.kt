package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.ui.model.AlbumDownloadItem

enum class SortOrder {
    TITLE, ARTIST
}

enum class UIState {
    LOADING, SUCCESS, EMPTY, ERROR, UNAUTHENTICATED
}

class MainViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIState.LOADING)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val songs: StateFlow<List<SyncSong>> = repository.getSyncSongsFlow()
        .combine(_searchQuery) { songs, query ->
            songs.filter { 
                it.song.title.contains(query, ignoreCase = true) || 
                it.song.artist.contains(query, ignoreCase = true) || 
                it.song.album.contains(query, ignoreCase = true)
            }
        }.combine(_sortOrder) { songs, sort ->
            songs.sortedBy { 
                if (sort == SortOrder.TITLE) it.song.title.lowercase() else it.song.artist.lowercase()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = repository.getAlbumsFlow()
        .combine(_searchQuery) { albums, query ->
            albums.filter { 
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }.sortedBy { it.title.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadQueue: StateFlow<List<AlbumDownloadItem>> = songs
        .map { allSyncSongs ->
            allSyncSongs.groupBy { it.albumId }.mapNotNull { (albumId, albumSyncSongs) ->
                val activeSyncSongs = albumSyncSongs.filter { 
                    it.downloadStatus != DownloadStatus.COMPLETED && it.downloadStatus != DownloadStatus.NONE 
                }
                if (activeSyncSongs.isEmpty()) return@mapNotNull null
                
                val first = albumSyncSongs.first()
                
                // Group by file (data URL) to calculate real progress based on all files in the album
                val uniqueFiles = albumSyncSongs.groupBy { it.data }.values.map { it.first() }
                val totalProgress = if (uniqueFiles.isNotEmpty()) {
                    val completedSum = uniqueFiles.sumOf { 
                        if (it.downloadStatus == DownloadStatus.COMPLETED) 100 else it.downloadProgress 
                    }
                    completedSum.toFloat() / (uniqueFiles.size * 100f)
                } else 0f

                val downloading = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.DOWNLOADING }
                val uniqueDownloading = downloading.distinctBy { it.data }
                
                val pending = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.PENDING }
                val error = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.ERROR }

                if (first.song.type == AlbumType.CUE) {
                    AlbumDownloadItem.Cue(
                        albumId = albumId,
                        title = first.song.album,
                        artist = first.song.artist,
                        imageUrl = first.song.imageUrl,
                        progress = totalProgress,
                        totalSongs = albumSyncSongs.size,
                        downloadingSongs = uniqueDownloading,
                        pendingSongsCount = pending.size,
                        errorSongsCount = error.size,
                        isDownloading = downloading.isNotEmpty()
                    )
                } else {
                    AlbumDownloadItem.Normal(
                        albumId = albumId,
                        title = first.song.album,
                        artist = first.song.artist,
                        imageUrl = first.song.imageUrl,
                        progress = totalProgress,
                        totalSongs = albumSyncSongs.size,
                        downloadingSongs = uniqueDownloading,
                        pendingSongsCount = pending.size,
                        errorSongsCount = error.size,
                        isDownloading = downloading.isNotEmpty()
                    )
                }
            }.sortedByDescending { it.isDownloading }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSongs()
        viewModelScope.launch {
            albums.collect { 
                if (it.isNotEmpty() && (_uiState.value == UIState.LOADING || _uiState.value == UIState.EMPTY)) {
                    _uiState.value = UIState.SUCCESS
                }
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            if (albums.value.isEmpty()) {
                _uiState.value = UIState.LOADING
            }
            try {
                repository.syncAll()
                _uiState.value = UIState.SUCCESS
            } catch (_: Exception) {
                if (albums.value.isEmpty()) {
                    _uiState.value = UIState.ERROR
                }
            }
        }
    }

    fun setUnauthenticated() {
        _uiState.value = UIState.UNAUTHENTICATED
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun getSongsForAlbum(albumTitle: String): List<SyncSong> {
        return songs.value.filter { it.song.album == albumTitle }
    }

    private val _processingMetadataIds = MutableStateFlow<Set<String>>(emptySet())

    fun fetchMetadataForSongs(songsToProcess: List<SyncSong>) {
        viewModelScope.launch {
            val idsToProcess = songsToProcess
                .filter { it.localPath != null && it.song.artist == "Unknown Artist" }
                .map { it.song.id }
                .filter { it !in _processingMetadataIds.value }

            if (idsToProcess.isEmpty()) return@launch

            _processingMetadataIds.update { it + idsToProcess }

            idsToProcess.forEach { id ->
                repository.fetchMetadata(id)
            }
        }
    }

    fun downloadSongs(songs: List<SyncSong>) {
        viewModelScope.launch {
            songs.forEach { song ->
                if (song.localPath == null) {
                    repository.downloadSong(song.song.id)
                }
            }
        }
    }
}
