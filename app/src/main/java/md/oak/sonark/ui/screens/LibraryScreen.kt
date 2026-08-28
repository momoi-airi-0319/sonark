package md.oak.sonark.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.Artist
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.FloatingNavItem
import md.oak.sonark.ui.screens.library.LibraryArtists
import md.oak.sonark.ui.screens.library.LibraryGrid
import md.oak.sonark.ui.screens.library.LibrarySongs
import md.oak.sonark.ui.theme.SonarkTheme

enum class LibraryTab(val label: String, val icon: ImageVector) {
    Artists("Artists", Icons.Rounded.Mic),
    Albums("Albums", Icons.Rounded.Album),
    Songs("Songs", Icons.Rounded.MusicNote)
}

@Composable
fun LibraryScreen(
    uiState: UIState,
    albums: List<Album>,
    artists: List<Artist>,
    songs: List<SyncSong>,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSongClick: (SyncSong) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Albums) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp + 24.dp
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 132.dp + 24.dp

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                LibraryTab.Artists -> {
                    LibraryArtists(
                        uiState = uiState,
                        artists = artists,
                        onArtistClick = onArtistClick,
                        onRefresh = onRefresh,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = topPadding,
                            end = 16.dp,
                            bottom = bottomPadding
                        )
                    )
                }
                LibraryTab.Albums -> {
                    LibraryGrid(
                        uiState = uiState,
                        albums = albums,
                        onAlbumClick = onAlbumClick,
                        onRefresh = onRefresh,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = topPadding,
                            end = 16.dp,
                            bottom = bottomPadding
                        )
                    )
                }
                LibraryTab.Songs -> {
                    LibrarySongs(
                        uiState = uiState,
                        songs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        progress = progress,
                        onSongClick = onSongClick,
                        onRefresh = onRefresh,
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = topPadding,
                            end = 0.dp,
                            bottom = bottomPadding
                        )
                    )
                }
            }

            LibrarySubNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun LibrarySubNavigation(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 68.dp + 12.dp) // Stacks 12dp above the main floating bottom bar (52dp + 16dp)
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
                        onClick = { onTabSelected(tab) },
                        icon = tab.icon,
                        label = tab.label,
                        horizontalPadding = 12.dp
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenLoadingPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.LOADING,
            albums = emptyList(),
            artists = emptyList(),
            songs = emptyList(),
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            onAlbumClick = {},
            onArtistClick = {},
            onSongClick = {},
            onRefresh = {}
        )
    }
}
