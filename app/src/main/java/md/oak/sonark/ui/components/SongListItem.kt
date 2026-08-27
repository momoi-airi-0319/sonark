package md.oak.sonark.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.utils.Formatter

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.theme.SonarkTheme
import md.oak.sonark.data.model.Song
import java.util.UUID

@Composable
fun SongListItem(
    syncSong: SyncSong,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = syncSong.song
    val isDownloaded = syncSong.localPath != null
    
    ListItem(
        modifier = modifier.clickable(enabled = isDownloaded || isCurrent) { onClick() },
        leadingContent = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                AnimatedContent(
                    targetState = isCurrent,
                    transitionSpec = {
                        scaleIn(initialScale = 0f, animationSpec = tween(400, delayMillis = 400))
                            .togetherWith(scaleOut(targetScale = 0f, animationSpec = tween(400)))
                    },
                    label = "leadingTransition"
                ) { targetIsCurrent ->
                    if (targetIsCurrent) {
                        CircularWavyProgressIndicator(
                            progress = progress,
                            isPlaying = isPlaying,
                            imageUrl = null,
                            showImage = false,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier.size(6.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {}
                        }
                    }
                }
            }
        },
        headlineContent = {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else if (isDownloaded) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                } else if (isDownloaded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (isDownloaded) {
                Text(
                    text = Formatter.formatDuration(song.duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.End
                )
            } else if (syncSong.downloadStatus == DownloadStatus.DOWNLOADING) {
                CircularProgressIndicator(
                    progress = { syncSong.downloadProgress / 100f },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SongListItemPreview() {
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
        localPath = "/music/simple_love.mp3"
    )

    SonarkTheme {
        Column {
            SongListItem(
                syncSong = syncSong,
                isCurrent = true,
                isPlaying = true,
                progress = 0.4f,
                onClick = {}
            )
            SongListItem(
                syncSong = syncSong.copy(localPath = null),
                isCurrent = false,
                isPlaying = false,
                progress = 0f,
                onClick = {}
            )
        }
    }
}
