package md.oak.sonark.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.components.WavySlider
import md.oak.sonark.ui.utils.Formatter

import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.components.MetadataItem
import md.oak.sonark.ui.theme.SonarkTheme
import md.oak.sonark.data.model.Song
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: SyncSong?,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    var showSongDetails by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = "Now Playing",
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
                    if (song != null) {
                        IconButton(onClick = { showSongDetails = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Song Details",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (song != null) {
                val metadata = song.song
                Spacer(modifier = Modifier.weight(1f))

                AlbumArt(
                    imageUrl = metadata.imageUrl,
                    type = metadata.type,
                    onAlbumClick = { onAlbumClick(metadata.album) }
                )

                Spacer(modifier = Modifier.height(48.dp))

                SongInfo(
                    title = metadata.title,
                    artist = metadata.artist
                )

                Spacer(modifier = Modifier.height(32.dp))

                key(metadata.id) {
                    PlaybackSeekBar(
                        progress = progress,
                        duration = duration,
                        isPlaying = isPlaying,
                        onSeekTo = onSeekTo
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                PlayerControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onTogglePlayback = onTogglePlayback,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeatMode = onToggleRepeatMode
                )

                Spacer(modifier = Modifier.weight(1.5f))
            } else {
                EmptyPlayerState()
            }
        }

        if (showSongDetails && (song != null)) {
            SongDetailsDialog(
                song = song,
                onDismiss = { showSongDetails = false },
            )
        }
    }
}

@Composable
private fun AlbumArt(
    imageUrl: String?,
    type: AlbumType,
    onAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onAlbumClick,
        modifier = modifier
            .size(300.dp)
            .aspectRatio(1f)
            .clip(if (type == AlbumType.CUE) CircleShape else MaterialTheme.shapes.extraLarge),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SongInfo(
    title: String,
    artist: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1
        )
    }
}

@Composable
private fun PlaybackSeekBar(
    progress: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        WavySlider(
            value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
            onValueChange = { onSeekTo((it * duration).toLong()) },
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Formatter.formatDuration(progress),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = Formatter.formatDuration(duration),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onTogglePlayback: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onSkipPrevious) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(40.dp)
                )
            }

            FilledIconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        IconButton(onClick = onToggleRepeatMode) {
            val icon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            }
            Icon(
                imageVector = icon,
                contentDescription = "Repeat",
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyPlayerState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Select a song to play", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SongDetailsDialog(
    song: SyncSong,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("歌曲详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataItem("标题", song.song.title)
                MetadataItem("艺术家", song.song.artist)
                MetadataItem("专辑", song.song.album)
                MetadataItem("时长", Formatter.formatDuration(song.song.duration))
                song.localPath?.let {
                    MetadataItem("本地路径", it)
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PlayerScreenPreview() {
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
    val syncSong = SyncSong(
        song = song,
        data = "fileId",
        albumId = "albumId",
        localPath = null
    )

    SonarkTheme {
        PlayerScreen(
            song = syncSong,
            isPlaying = true,
            progress = 120000,
            duration = 270000,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            onTogglePlayback = {},
            onSeekTo = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onToggleShuffle = {},
            onToggleRepeatMode = {},
            onBackClick = {},
            onAlbumClick = {}
        )
    }
}
