package md.oak.sonark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import md.oak.sonark.ui.model.AlbumDownloadItem
import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.theme.SonarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueBottomSheet(
    queue: List<AlbumDownloadItem>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseSong: (String) -> Unit,
    onResumeSong: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download Queue",
                    style = MaterialTheme.typography.titleLarge,
                )
                
                Row {
                    IconButton(onClick = onPauseAll) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause All")
                    }
                    IconButton(onClick = onResumeAll) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
                    }
                }
            }
            
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active downloads", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(queue, key = { it.albumId }) { item ->
                        AlbumDownloadTaskItem(
                            item = item,
                            onPauseSong = onPauseSong,
                            onResumeSong = onResumeSong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDownloadTaskItem(
    item: AlbumDownloadItem,
    onPauseSong: (String) -> Unit,
    onResumeSong: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            leadingContent = {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            },
            headlineContent = {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column {
                    Text(
                        text = "${item.artist} • ${item.totalSongs} 首曲目 • ${item.getStatusText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isUserPaused || item.pauseReason != null) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = if (item.isDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // UI pause button ONLY controls isUserPaused state
                    if (item.isUserPaused) {
                        IconButton(onClick = { onResumeSong(item.albumId) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    } else {
                        IconButton(onClick = { onPauseSong(item.albumId) }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
        )

        AnimatedVisibility(visible = expanded) {
            item.DetailContent(
                modifier = Modifier
                    .padding(start = 72.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
