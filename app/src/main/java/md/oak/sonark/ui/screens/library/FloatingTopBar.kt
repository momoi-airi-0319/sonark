package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.components.CircularWavyProgressIndicator
import md.oak.sonark.ui.components.UserAvatar
import md.oak.sonark.ui.theme.SonarkTheme

@Composable
fun FloatingTopBar(
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    activeAccount: UserAccount?,
    onPlayerClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Pill
        Surface(
            onClick = onAccountClick,
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                UserAvatar(
                    user = activeAccount,
                    size = 44.dp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Playback Status Pill
        Surface(
            onClick = onPlayerClick,
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .height(56.dp)
                .weight(1f, fill = false)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (currentSong != null) 6.dp else 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            ) {
                if (currentSong != null) {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        isPlaying = isPlaying,
                        imageUrl = currentSong.song.imageUrl,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentSong?.song?.title ?: "Sonark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingTopBarEmptyPreview() {
    SonarkTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTopBar(
                currentSong = null,
                isPlaying = false,
                progress = 0f,
                activeAccount = null,
                onPlayerClick = {},
                onAccountClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingTopBarWithAccountPreview() {
    SonarkTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingTopBar(
                currentSong = null,
                isPlaying = false,
                progress = 0f,
                activeAccount = UserAccount("Airi", "airi@example.com"),
                onPlayerClick = {},
                onAccountClick = {}
            )
        }
    }
}
