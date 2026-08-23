package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import md.oak.sonark.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumTitle: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onLoadMetadata: (List<Song>) -> Unit,
    onDownloadSongs: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(songs) {
        onDownloadSongs(songs)
    }

    val songsToLoadMetadata = remember(songs) { 
        songs.filter { it.localPath != null && it.artist == "Unknown Artist" } 
    }
    LaunchedEffect(songsToLoadMetadata) {
        if (songsToLoadMetadata.isNotEmpty()) {
            onLoadMetadata(songsToLoadMetadata)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumTitle) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item {
                AlbumHeader(songs.firstOrNull())
            }
            items(songs) { song ->
                ListItem(
                    headlineContent = { Text(song.title) },
                    supportingContent = { Text(song.artist) },
                    leadingContent = {
                        if (song.localPath != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Cached")
                        }
                    },
                    modifier = Modifier.clickable { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = if (song?.isCueAlbum == true) CircleShape else MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SubcomposeAsyncImage(
                model = song?.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = song?.album?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = song?.album ?: "Unknown Album",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = song?.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
    }
}
