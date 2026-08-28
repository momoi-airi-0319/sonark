package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.repository.MusicRepository

data class SearchUiState(
    val songs: List<SyncSong> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<String> = emptyList()
)

class SearchViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<SearchUiState> = combine(
        repository.getSyncSongsFlow(),
        repository.getAlbumsFlow(),
        _searchQuery
    ) { songs, albums, query ->
        if (query.isBlank()) return@combine SearchUiState()

        val filteredSongs = songs.filter { it.song.title.contains(query, ignoreCase = true) }
            .sortedBy { it.song.title.lowercase() }

        val filteredAlbums = albums.filter { it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }

        val filteredArtists = (songs.map { it.song.artist } + albums.map { it.artist })
            .filter { it.contains(query, ignoreCase = true) }
            .distinct()
            .sortedBy { it.lowercase() }

        SearchUiState(filteredSongs, filteredAlbums, filteredArtists)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
