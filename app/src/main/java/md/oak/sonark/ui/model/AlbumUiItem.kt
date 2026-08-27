package md.oak.sonark.ui.model

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import md.oak.sonark.data.model.Album

sealed class AlbumUiItem {
    abstract val album: Album

    @Composable
    abstract fun getImageShape(): Shape

    @Composable
    fun GridItem(onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AlbumImage(modifier = Modifier.aspectRatio(1f).fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    fun ListItem(onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumImage(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    fun Header() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AlbumImage(modifier = Modifier.size(200.dp))
        }
    }

    @Composable
    protected fun AlbumImage(modifier: Modifier) {
        Surface(
            modifier = modifier,
            shape = getImageShape(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SubcomposeAsyncImage(
                model = album.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = album.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
            )
        }
    }

    data class Normal(override val album: Album) : AlbumUiItem() {
        @Composable
        override fun getImageShape(): Shape = MaterialTheme.shapes.medium
    }

    data class Cue(override val album: Album) : AlbumUiItem() {
        @Composable
        override fun getImageShape(): Shape = CircleShape
    }

    companion object {
        fun from(album: Album): AlbumUiItem = when (album) {
            is Album.Normal -> Normal(album)
            is Album.Cue -> Cue(album)
        }
    }
}
