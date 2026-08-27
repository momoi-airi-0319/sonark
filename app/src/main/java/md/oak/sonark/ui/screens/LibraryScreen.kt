package md.oak.sonark.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.FloatingNavItem
import md.oak.sonark.ui.screens.library.AccountPopDialog
import md.oak.sonark.ui.screens.library.FloatingTopBar
import md.oak.sonark.ui.screens.library.LibraryGrid
import md.oak.sonark.ui.theme.SonarkTheme

enum class LibraryTab(val label: String, val icon: ImageVector) {
    Playlists("Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay),
    Artists("Artists", Icons.Rounded.Mic),
    Albums("Albums", Icons.Rounded.Album),
    Songs("Songs", Icons.Rounded.MusicNote),
    Recent("Recent", Icons.Rounded.Schedule)
}

@Composable
fun LibraryScreen(
    uiState: UIState,
    albums: List<Album>,
    googleAccountName: String?,
    downloadQueueSize: Int,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
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
    var selectedTab by remember { mutableStateOf(LibraryTab.Recent) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LibraryGrid(
                uiState = uiState,
                albums = albums,
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

            // Library Sub-navigation Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 68.dp + 12.dp) // Positioned above the main nav bar (52dp + 16dp + 12dp gap)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    tonalElevation = 6.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LibraryTab.entries.forEach { tab ->
                            FloatingNavItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = tab.icon,
                                label = tab.label,
                                horizontalPadding = 12.dp
                            )
                        }
                    }
                }
            }
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
