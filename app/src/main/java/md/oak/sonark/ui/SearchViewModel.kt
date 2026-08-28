package md.oak.sonark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.ui.utils.ArtistUtils

data class SearchUiState(
    val songs: List<SyncSong> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<String> = emptyList(),
)

class SearchViewModel(
    repository: MusicRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<SearchUiState> = combine(
        repository.getSyncSongsFlow(),
        repository.getAlbumsFlow(),
        _searchQuery
    ) { songs, albums, query ->
        if (query.isBlank()) return@combine SearchUiState()

        val filteredSongs = songs.asSequence()
            .filter { it.song.title.contains(query, ignoreCase = true) }
            .sortedBy { it.song.title.lowercase() }
            .toList()

        val filteredAlbums = albums.asSequence()
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
            .toList()

        val filteredArtists = (songs.flatMap { ArtistUtils.splitArtists(it.song.artist) } +
                               albums.flatMap { ArtistUtils.splitArtists(it.artist) })
            .asSequence()
            .filter { it.contains(query, ignoreCase = true) }
            .groupBy { ArtistUtils.normalize(it) }
            .map { (_, variations) ->
                // Optimized weighting for search:
                // Primary: Is this name used as an Album Artist?
                // Secondary: Total occurrences in songs
                // Tertiary: Lexicographical
                variations.distinct().sortedWith(
                    compareByDescending<String> { name ->
                        albums.count { it.artist == name }
                    }.thenByDescending { name ->
                        songs.count { it.song.artist == name }
                    }.thenByDescending { it }
                ).first()
            }
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
