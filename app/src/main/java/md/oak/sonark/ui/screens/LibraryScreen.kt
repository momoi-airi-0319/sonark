package md.oak.sonark.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.screens.library.AccountPopDialog
import md.oak.sonark.ui.screens.library.FloatingTopBar
import md.oak.sonark.ui.screens.library.LibraryGrid
import md.oak.sonark.ui.theme.SonarkTheme

@Composable
fun LibraryScreen(
    uiState: UIState,
    albums: List<Album>,
    googleAccountName: String?,
    downloadQueueSize: Int,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayerClick: () -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LibraryGrid(
                uiState = uiState,
                albums = albums,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onAlbumClick = onAlbumClick,
                onRefresh = onRefresh
            )

            FloatingTopBar(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = progress,
                googleAccountName = googleAccountName,
                onPlayerClick = onPlayerClick,
                onAccountClick = { showAccountDialog = true }
            )
        }
    }

    if (showAccountDialog) {
        AccountPopDialog(
            googleAccountName = googleAccountName,
            downloadQueueSize = downloadQueueSize,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            onRefresh = onRefresh,
            onQueueClick = {
                showAccountDialog = false
                onQueueClick()
            },
            onSettingsClick = {
                showAccountDialog = false
                onSettingsClick()
            },
            onDismissRequest = { showAccountDialog = false }
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenLoadingPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.LOADING,
            albums = emptyList(),
            googleAccountName = null,
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenEmptyPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.EMPTY,
            albums = emptyList(),
            googleAccountName = "user@gmail.com",
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenUnauthenticatedPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.UNAUTHENTICATED,
            albums = emptyList(),
            googleAccountName = null,
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}
