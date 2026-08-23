package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import md.oak.sonark.data.model.Song
import md.oak.sonark.ui.components.WavySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    queue: List<Song>,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQueue by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue"
                        )
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
                Spacer(modifier = Modifier.weight(1f))

                // Album Art
                Surface(
                    modifier = Modifier
                        .size(300.dp)
                        .aspectRatio(1f)
                        .clip(if (song.isCueAlbum) CircleShape else MaterialTheme.shapes.extraLarge),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (song.imageUrl != null) {
                        AsyncImage(
                            model = song.imageUrl,
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

                Spacer(modifier = Modifier.height(48.dp))

                // Song Info
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Progress Bar
                Column {
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
                            text = formatTime(progress),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
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

                Spacer(modifier = Modifier.weight(1.5f))
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a song to play", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(queue) { queuedSong ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    queuedSong.title,
                                    fontWeight = if (queuedSong == song) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            supportingContent = { Text(queuedSong.artist) },
                            leadingContent = {
                                Icon(
                                    imageVector = if (queuedSong == song) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (queuedSong == song) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            },
                            modifier = Modifier.clickable { /* Could implement play from queue here */ }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return "%d:%02d".format(minutes, seconds)
}
