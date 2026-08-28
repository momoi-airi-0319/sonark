package md.oak.sonark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.components.MetadataItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    personalAlbums: List<Album>,
    featuredSongs: List<SyncSong>,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (SyncSong) -> Unit,
    onBackClick: () -> Unit,
    onLoadMetadata: (List<SyncSong>) -> Unit,
    onDownloadSongs: (List<SyncSong>) -> Unit,
) {
    BackHandler(onBack = onBackClick)
    var showDetails by remember { mutableStateOf(value = false) }

    LaunchedEffect(key1 = featuredSongs) {
        onDownloadSongs(featuredSongs)
    }

    val songsToLoadMetadata = remember(featuredSongs) {
        featuredSongs.filter { (it.localPath != null) && (it.song.artist == "Unknown Artist") }
    }
    LaunchedEffect(songsToLoadMetadata) {
        if (songsToLoadMetadata.isNotEmpty()) {
            onLoadMetadata(songsToLoadMetadata)
        }
    }

    // Use the first available image as a candidate for the header avatar, 
    // but the user wants to "give up on automatic setting" - we'll show it only in the header if available.
    val avatarUrl = remember(personalAlbums, featuredSongs) {
        personalAlbums.firstOrNull { it.imageUrl != null }?.imageUrl
            ?: featuredSongs.firstOrNull { it.song.imageUrl != null }?.song?.imageUrl
    }

    if (showDetails) {
        ArtistDetailsDialog(
            artistName = artistName,
            albumCount = personalAlbums.size,
            songCount = personalAlbums.sumOf { it.songs.size } + featuredSongs.size,
            onDismiss = { showDetails = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = artistName,
                        textAlign = TextAlign.Center
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDetails = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (personalAlbums.isEmpty() && featuredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No music found for this artist", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    ArtistHeader(artistName, avatarUrl)
                }

                if (personalAlbums.isNotEmpty()) {
                    item {
                        SectionHeader("Personal Albums")
                    }
                    item {
                        val columns = 2
                        val rows = (personalAlbums.size + columns - 1) / columns
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            for (i in 0 until rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    for (j in 0 until columns) {
                                        val index = i * columns + j
                                        if (index < personalAlbums.size) {
                                            val album = personalAlbums[index]
                                            Box(modifier = Modifier.weight(1f)) {
                                                val uiItem = remember(album) { md.oak.sonark.ui.model.AlbumUiItem.from(album) }
                                                uiItem.GridItem(onClick = { onAlbumClick(album) })
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (featuredSongs.isNotEmpty()) {
                    item {
                        SectionHeader("Featured In / Other Tracks")
                    }
                    items(featuredSongs, key = { it.song.id }) { syncSong ->
                        md.oak.sonark.ui.components.SongListItem(
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

@Composable
private fun ArtistHeader(name: String, imageUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ArtistDetailsDialog(
    artistName: String,
    albumCount: Int,
    songCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("艺术家详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataItem("名称", artistName)
                MetadataItem("个人专辑数量", albumCount.toString())
                MetadataItem("总关联曲目数", songCount.toString())
            }
        }
    )
}
