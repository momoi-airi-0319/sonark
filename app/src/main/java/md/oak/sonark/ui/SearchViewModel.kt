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
    val artists: List<String> = emptyList(),
)

class SearchViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<SearchUiState> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .map { query ->
            if (query.isBlank()) return@map SearchUiState()

            val songs = repository.searchSongs(query)
            val albums = repository.albumsFlow.value.filter { it.title.contains(query, ignoreCase = true) }
            val artists = repository.artistsFlow.value.filter { it.name.contains(query, ignoreCase = true) }.map { it.name }

            SearchUiState(songs, albums, artists)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
