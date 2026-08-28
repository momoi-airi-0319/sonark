package md.oak.sonark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.components.MetadataItem
import md.oak.sonark.ui.components.SongListItem
import md.oak.sonark.ui.model.AlbumUiItem
import md.oak.sonark.ui.theme.SonarkTheme
import md.oak.sonark.ui.utils.Formatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album,
    songs: List<SyncSong>,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    onSongClick: (SyncSong) -> Unit,
    onBackClick: () -> Unit,
    onLoadMetadata: (List<SyncSong>) -> Unit,
    onDownloadSongs: (List<SyncSong>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    var showDetails by remember { mutableStateOf(value = false) }

    LaunchedEffect(key1 = songs) {
        onDownloadSongs(songs)
    }

    val songsToLoadMetadata = remember(songs) {
        songs.filter { (it.localPath != null) && (it.song.artist == "Unknown Artist") }
    }
    LaunchedEffect(songsToLoadMetadata) {
        if (songsToLoadMetadata.isNotEmpty()) {
            onLoadMetadata(songsToLoadMetadata)
        }
    }

    if (showDetails) {
        AlbumDetailsDialog(
            album = album,
            songs = songs,
            onDismiss = { showDetails = false },
        )
    }

    val groupedSongs = remember(songs) {
        songs.groupBy { it.song.discNumber }
            .toSortedMap()
    }
    val showDiscHeaders = remember(groupedSongs) {
        groupedSongs.size > 1 || (groupedSongs.size == 1 && groupedSongs.firstKey() != 0)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = album.title,
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
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                val uiItem = remember(album) { AlbumUiItem.from(album) }
                uiItem.Header()
            }

            groupedSongs.forEach { (discNumber, discSongs) ->
                if (showDiscHeaders) {
                    item(key = "disc_$discNumber") {
                        DiscHeader(discNumber)
                    }
                }

                items(
                    items = discSongs.sortedBy { it.song.trackNumber },
                    key = { it.song.id }
                ) { syncSong ->
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

@Composable
private fun DiscHeader(
    discNumber: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (discNumber == 0) "其他" else "Disc $discNumber",
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AlbumDetailsDialog(
    album: Album,
    songs: List<SyncSong>,
    onDismiss: () -> Unit
) {
    val totalDuration = remember(songs) {
        songs.sumOf { it.song.duration }
    }
    val formattedDuration = remember(totalDuration) {
        Formatter.formatDuration(totalDuration)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("专辑详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataItem("名称", album.title)
                MetadataItem("艺术家", album.artist)
                MetadataItem("类型", if (album is Album.Cue) "CUE 专辑" else "标准专辑")
                MetadataItem("曲目数量", songs.size.toString())
                MetadataItem("总时长", formattedDuration)
                album.localPath?.let {
                    MetadataItem("本地路径", it)
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AlbumScreenPreview() {
    val album = Album.Normal(
        title = "Fantasy",
        artist = "Jay Chou",
        localPath = "/music/jay",
        imageUrl = null,
        songs = emptyList()
    )
    val song = Song(
        id = UUID.randomUUID().toString(),
        title = "Simple Love",
        artist = "Jay Chou",
        album = "Fantasy",
        duration = 270,
        trackNumber = 1,
        discNumber = 1,
        imageUrl = null
    )
    val syncSongs = listOf(
        SyncSong(
            song = song,
            data = "fileId",
            albumId = "albumId",
            localPath = null
        )
    )

    SonarkTheme {
        AlbumScreen(
            album = album,
            songs = syncSongs,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            onSongClick = {},
            onBackClick = {},
            onLoadMetadata = {},
            onDownloadSongs = {}
        )
    }
}
