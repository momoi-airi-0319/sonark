package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.repository.MusicRepository

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

    val songs: StateFlow<List<Song>> = repository.getSongsFlow()
        .combine(_searchQuery) { songs, query ->
            songs.filter { 
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) || it.album.contains(query, ignoreCase = true)
            }
        }.combine(_sortOrder) { songs, sort ->
            songs.sortedBy { 
                if (sort == SortOrder.TITLE) it.title.lowercase() else it.artist.lowercase()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = songs.map { songList ->
        songList.groupBy { it.album }.map { (albumTitle, albumSongs) ->
            Album(
                title = albumTitle,
                artist = albumSongs.firstOrNull { it.artist != "Unknown Artist" }?.artist 
                    ?: albumSongs.firstOrNull()?.artist 
                    ?: "Unknown Artist",
                imageUrl = albumSongs.firstOrNull { it.imageUrl != null }?.imageUrl,
                songs = albumSongs
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSong = MutableStateFlow<Song?>(null)

    init {
        loadSongs()
    }

    fun loadSongs() {
        viewModelScope.launch {
            if (!repository.isServiceSet()) {
                setUnauthenticated()
                return@launch
            }
            
            _uiState.value = UIState.LOADING
            try {
                repository.syncWithDrive()
                _uiState.value = UIState.SUCCESS
            } catch (_: Exception) {
                _uiState.value = UIState.ERROR
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

    fun getSongsForAlbum(albumTitle: String): List<Song> {
        return songs.value.filter { it.album == albumTitle }
    }

    private val _processingMetadataIds = MutableStateFlow<Set<String>>(emptySet())

    fun fetchMetadataForSongs(songsToProcess: List<Song>) {
        viewModelScope.launch {
            val idsToProcess = songsToProcess
                .filter { it.localPath != null && it.artist == "Unknown Artist" }
                .map { it.id }
                .filter { it !in _processingMetadataIds.value }

            if (idsToProcess.isEmpty()) return@launch

            _processingMetadataIds.update { it + idsToProcess }

            idsToProcess.forEach { id ->
                repository.fetchMetadata(id)
            }
        }
    }

    fun downloadSongs(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { song ->
                if (song.localPath == null) {
                    repository.downloadSong(song)
                }
            }
        }
    }
}
