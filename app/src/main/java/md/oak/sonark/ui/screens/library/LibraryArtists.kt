package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import md.oak.sonark.data.model.Artist
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.EmptyState

@Composable
fun LibraryArtists(
    uiState: UIState,
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
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
                    description = "An error occurred while loading artists. Please try again.",
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.EMPTY -> {
                EmptyState(
                    icon = Icons.Rounded.Mic,
                    message = "No Artists Found",
                    description = "Try syncing your library to see your artists here.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.SUCCESS -> {
                if (artists.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Mic,
                        message = "No Artists Found",
                        description = "No artists were found in your library.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = contentPadding,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(artists, key = { it.name }) { artist ->
                            ArtistGridItem(
                                artist = artist,
                                onClick = { onArtistClick(artist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistGridItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SubcomposeAsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = artist.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        val subtitle = buildString {
            if (artist.albumCount > 0) {
                append(if (artist.albumCount == 1) "1 Album" else "${artist.albumCount} Albums")
            }
            if (artist.albumCount > 0 && artist.songCount > 0) {
                append(" • ")
            }
            if (artist.songCount > 0) {
                append(if (artist.songCount == 1) "1 Song" else "${artist.songCount} Songs")
            }
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
