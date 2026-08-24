package md.oak.sonark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.ui.model.AlbumDownloadItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueBottomSheet(
    queue: List<AlbumDownloadItem>,
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
            Text(
                text = "Download Queue",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
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
                        AlbumDownloadTaskItem(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDownloadTaskItem(item: AlbumDownloadItem) {
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
                        text = "${item.artist} • ${item.totalSongs} tracks",
                        style = MaterialTheme.typography.bodySmall
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
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
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
