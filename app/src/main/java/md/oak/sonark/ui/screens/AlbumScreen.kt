package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.model.AlbumUiItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album,
    songs: List<SyncSong>,
    onSongClick: (SyncSong) -> Unit,
    onBackClick: () -> Unit,
    onLoadMetadata: (List<SyncSong>) -> Unit,
    onDownloadSongs: (List<SyncSong>) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(songs) {
        onDownloadSongs(songs)
    }

    val songsToLoadMetadata = remember(songs) { 
        songs.filter { it.localPath != null && it.song.artist == "Unknown Artist" } 
    }
    LaunchedEffect(songsToLoadMetadata) {
        if (songsToLoadMetadata.isNotEmpty()) {
            onLoadMetadata(songsToLoadMetadata)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.title) },
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
                val uiItem = remember(album) { AlbumUiItem.from(album) }
                uiItem.Header()
            }
            items(songs) { syncSong ->
                val song = syncSong.song
                val isDownloaded = syncSong.localPath != null
                ListItem(
                    headlineContent = { 
                        Text(
                            text = song.title,
                            color = if (isDownloaded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ) 
                    },
                    supportingContent = { 
                        Column {
                            Text(
                                text = song.artist,
                                color = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            if (syncSong.downloadStatus == DownloadStatus.DOWNLOADING) {
                                LinearProgressIndicator(
                                    progress = { syncSong.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    leadingContent = {
                        when (syncSong.downloadStatus) {
                            DownloadStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
                            DownloadStatus.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            DownloadStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                            else -> Icon(Icons.Default.CloudDownload, contentDescription = "Not Downloaded", tint = MaterialTheme.colorScheme.outline)
                        }
                    },
                    modifier = Modifier.clickable(enabled = isDownloaded) { onSongClick(syncSong) }
                )
            }
        }
    }
}
