package md.oak.sonark.ui.model

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.Utils
import md.oak.sonark.data.model.SyncSong

sealed class AlbumDownloadItem {
    abstract val albumId: String
    abstract val title: String
    abstract val artist: String
    abstract val imageUrl: String?
    abstract val progress: Float
    abstract val totalSongs: Int
    abstract val downloadingSongs: List<SyncSong>
    abstract val pendingSongsCount: Int
    abstract val errorSongsCount: Int
    abstract val isDownloading: Boolean

    @Composable
    abstract fun DetailContent(modifier: Modifier)

    data class Normal(
        override val albumId: String,
        override val title: String,
        override val artist: String,
        override val imageUrl: String?,
        override val progress: Float,
        override val totalSongs: Int,
        override val downloadingSongs: List<SyncSong>,
        override val pendingSongsCount: Int,
        override val errorSongsCount: Int,
        override val isDownloading: Boolean
    ) : AlbumDownloadItem() {
        @Composable
        override fun DetailContent(modifier: Modifier) {
            Column(modifier = modifier) {
                downloadingSongs.forEach { syncSong ->
                    SongProgressItem("${syncSong.song.trackNumber}. ${syncSong.song.title}", syncSong.size, syncSong.downloadProgress)
                }
                SummaryInfo()
            }
        }
    }

    data class Cue(
        override val albumId: String,
        override val title: String,
        override val artist: String,
        override val imageUrl: String?,
        override val progress: Float,
        override val totalSongs: Int,
        override val downloadingSongs: List<SyncSong>,
        override val pendingSongsCount: Int,
        override val errorSongsCount: Int,
        override val isDownloading: Boolean
    ) : AlbumDownloadItem() {
        @Composable
        override fun DetailContent(modifier: Modifier) {
            Column(modifier = modifier) {
                if (downloadingSongs.isNotEmpty()) {
                    // CUE only shows one progress for the whole disc file
                    val first = downloadingSongs.first()
                    SongProgressItem("Disc Audio File", first.size, first.downloadProgress)
                }
                SummaryInfo()
            }
        }
    }

    @Composable
    protected fun SummaryInfo() {
        if (errorSongsCount > 0) {
            Text(
                text = "$errorSongsCount tracks failed",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (pendingSongsCount > 0) {
            Text(
                text = "$pendingSongsCount tracks pending...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    protected fun SongProgressItem(title: String, size: Long, progress: Int) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Utils.formatSize(size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(top = 2.dp)
                )
            }
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
