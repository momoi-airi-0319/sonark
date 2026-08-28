package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.EmptyState
import md.oak.sonark.ui.components.SongListItem

@Composable
fun LibrarySongs(
    uiState: UIState,
    songs: List<SyncSong>,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onSongClick: (SyncSong) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues
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
                if (songs.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        message = "No Songs Found",
                        description = "No songs found in your 'Vault' directory on Google Drive.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(songs, key = { it.song.id }) { syncSong ->
                            SongListItem(
                                syncSong = syncSong,
                                isCurrent = syncSong.song.id == currentSong?.song?.id,
                                isPlaying = isPlaying,
                                progress = progress,
                                onClick = { onSongClick(syncSong) }
                            )
                        }
                    }
                }
            }
        }
    }
}
