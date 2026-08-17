package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.repository.MusicRepository

enum class SortOrder {
    TITLE, ARTIST
}

class MainViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(_allSongs, _searchQuery, _sortOrder) { songs, query, sort ->
        songs.filter { 
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }.sortedBy { 
            if (sort == SortOrder.TITLE) it.title.lowercase() else it.artist.lowercase()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong

    fun loadSongs() {
        viewModelScope.launch {
            _allSongs.value = repository.getLocalSongs()
        }
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
