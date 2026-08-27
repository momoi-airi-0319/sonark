package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.EmptyState
import md.oak.sonark.ui.model.AlbumUiItem

import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.theme.SonarkTheme

@Composable
fun LibraryGrid(
    uiState: UIState,
    albums: List<Album>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            UIState.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            UIState.UNAUTHENTICATED -> {
                EmptyState(
                    icon = Icons.Rounded.Security,
                    message = "Not Connected",
                    description = "Please connect your Google Drive account in Settings to access your music.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.ERROR -> {
                EmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    message = "Error Loading Library",
                    description = "An error occurred while loading songs. Please try again.",
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.EMPTY -> {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    message = "No Songs Found",
                    description = "No songs found in your 'Vault' directory on Google Drive.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.SUCCESS -> {
                if (albums.isEmpty() && searchQuery.isNotEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        message = "No Results",
                        description = "No albums found matching \"$searchQuery\"",
                        actionLabel = "Clear Search",
                        onAction = { onSearchQueryChange("") },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (albums.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        message = "No Songs Found",
                        description = "No songs found in your 'Vault' directory on Google Drive.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 160.dp, end = 16.dp, bottom = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.title }) { album ->
                            val uiItem = remember(album) { AlbumUiItem.from(album) }
                            uiItem.GridItem(onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LibraryGridLoadingPreview() {
    SonarkTheme {
        LibraryGrid(
            uiState = UIState.LOADING,
            albums = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            onAlbumClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LibraryGridErrorPreview() {
    SonarkTheme {
        LibraryGrid(
            uiState = UIState.ERROR,
            albums = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            onAlbumClick = {},
            onRefresh = {}
        )
    }
}
