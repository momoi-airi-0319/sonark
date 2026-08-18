package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

enum class SortOrder {
    TITLE, ARTIST
}

enum class UIState {
    LOADING, SUCCESS, EMPTY, ERROR, UNAUTHENTICATED
}

class MainViewModel(
    private val repository: MusicRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    
    private val _uiState = MutableStateFlow(UIState.LOADING)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(_allSongs, _searchQuery, _sortOrder) { songs, query, sort ->
        songs.filter { 
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) || it.album.contains(query, ignoreCase = true)
        }.sortedBy { 
            if (sort == SortOrder.TITLE) it.title.lowercase() else it.artist.lowercase()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = songs.map { songList ->
        songList.groupBy { it.album }.map { (albumTitle, albumSongs) ->
            Album(
                title = albumTitle,
                artist = albumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                imageUrl = albumSongs.firstOrNull()?.imageUrl,
                songs = albumSongs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong

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
                val songs = repository.getDriveSongs()
                _allSongs.value = songs
                _uiState.value = if (songs.isEmpty()) UIState.EMPTY else UIState.SUCCESS
            } catch (e: Exception) {
                _uiState.value = UIState.ERROR
            }
        }
    }

    fun setUnauthenticated() {
        _uiState.value = UIState.UNAUTHENTICATED
        _allSongs.value = emptyList()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun selectSong(song: Song) {
        _selectedSong.value = song
    }
}
