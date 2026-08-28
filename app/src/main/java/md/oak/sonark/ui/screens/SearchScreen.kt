package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.SearchUiState
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.components.SongListItem
import md.oak.sonark.ui.model.AlbumUiItem
import md.oak.sonark.ui.theme.SonarkTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onBackClick: () -> Unit,
    onSongClick: (SyncSong) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    SearchContent(
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        uiState = uiState,
        currentSong = currentSong,
        isPlaying = isPlaying,
        progress = progress,
        onBackClick = onBackClick,
        onSongClick = onSongClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    uiState: SearchUiState,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onBackClick: () -> Unit,
    onSongClick: (SyncSong) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search your music") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (searchQuery.isEmpty()) {
                EmptySearchState()
            } else {
                SearchResultsList(
                    uiState = uiState,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    progress = progress,
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick
                )
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Try searching for music",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SearchResultsList(
    uiState: SearchUiState,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onSongClick: (SyncSong) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 68.dp + 24.dp
        )
    ) {
        if (uiState.artists.isNotEmpty()) {
            item { SearchSectionHeader("Artists") }
            items(uiState.artists) { artist ->
                ArtistListItem(
                    artist = artist,
                    onClick = { onArtistClick(artist) },
                )
            }
        }

        if (uiState.albums.isNotEmpty()) {
            item { SearchSectionHeader("Albums") }
            items(uiState.albums) { album ->
                val uiItem = remember(album) { AlbumUiItem.from(album) }
                uiItem.ListItem(onClick = { onAlbumClick(album) })
            }
        }

        if (uiState.songs.isNotEmpty()) {
            item { SearchSectionHeader("Songs") }
            items(uiState.songs) { syncSong ->
                SongListItem(
                    syncSong = syncSong,
                    isCurrent = syncSong.song.id == currentSong?.song?.id,
                    isPlaying = isPlaying,
                    progress = progress,
                    onClick = { onSongClick(syncSong) }
                )
            }
        }

        if (uiState.songs.isEmpty() && uiState.albums.isEmpty() && uiState.artists.isEmpty()) {
            item {
                NoResultsFoundState()
            }
        }
    }
}

@Composable
private fun NoResultsFoundState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No results found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SearchSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ArtistListItem(artist: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = artist, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun SearchScreenPreview() {
    val songs = listOf(
        SyncSong(
            song = Song(
                id = UUID.randomUUID().toString(),
                title = "Simple Love",
                artist = "Jay Chou",
                album = "Fantasy",
                duration = 270,
                trackNumber = 1,
                discNumber = 1,
                imageUrl = null
            ),
            data = "fileId",
            albumId = "albumId"
        )
    )

    SonarkTheme {
        SearchContent(
            searchQuery = "Jay",
            onSearchQueryChange = {},
            uiState = SearchUiState(songs = songs, artists = listOf("Jay Chou")),
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            onBackClick = {},
            onSongClick = {},
            onAlbumClick = {},
            onArtistClick = {}
        )
    }
}
