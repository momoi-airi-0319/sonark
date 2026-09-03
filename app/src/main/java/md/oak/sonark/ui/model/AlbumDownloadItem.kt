package md.oak.sonark.ui.model

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.Utils
import md.oak.sonark.data.model.PauseReason
import md.oak.sonark.data.model.SyncSong

sealed class AlbumDownloadItem {
    abstract val albumId: String
    abstract val title: String
    abstract val artist: String
    abstract val imageUrl: String?
    abstract val progress: Float
    abstract val totalSongs: Int
    abstract val activeSongs: List<SyncSong>
    abstract val downloadingSongs: List<SyncSong>
    abstract val pendingSongsCount: Int
    abstract val errorSongsCount: Int
    abstract val isDownloading: Boolean
    abstract val isUserPaused: Boolean
    abstract val pauseReason: PauseReason?

    fun getStatusText(): String {
        return when {
            isUserPaused -> "已由用户暂停"
            pauseReason == PauseReason.METERED_NETWORK -> "流量计费网络已自动暂停"
            pauseReason == PauseReason.THREAD_LIMIT -> "排队等待中 (达到最大线程数)"
            isDownloading -> "正在下载 (${downloadingSongs.size} 线程)"
            errorSongsCount > 0 -> "部分曲目下载失败"
            else -> "等待中"
        }
    }

    @Composable
    abstract fun DetailContent(modifier: Modifier)

    data class Normal(
        override val albumId: String,
        override val title: String,
        override val artist: String,
        override val imageUrl: String?,
        override val progress: Float,
        override val totalSongs: Int,
        override val activeSongs: List<SyncSong>,
        override val downloadingSongs: List<SyncSong>,
        override val pendingSongsCount: Int,
        override val errorSongsCount: Int,
        override val isDownloading: Boolean,
        override val isUserPaused: Boolean,
        override val pauseReason: PauseReason?
    ) : AlbumDownloadItem() {
        @Composable
        override fun DetailContent(modifier: Modifier) {
            Column(modifier = modifier) {
                activeSongs.forEach { syncSong ->
                    val songStatusText = when {
                        syncSong.isUserPaused -> "用户已暂停"
                        syncSong.pauseReason == PauseReason.METERED_NETWORK -> "流量已暂停"
                        syncSong.pauseReason == PauseReason.THREAD_LIMIT -> "排队中"
                        syncSong.errorMessage != null -> "错误: ${syncSong.errorMessage}"
                        else -> "${syncSong.downloadProgress}%"
                    }
                    SongProgressItem(
                        title = "${syncSong.song.trackNumber}. ${syncSong.song.title}",
                        size = syncSong.size,
                        downloadedBytes = syncSong.downloadedBytes,
                        progress = syncSong.downloadProgress,
                        statusText = songStatusText
                    )
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
        override val activeSongs: List<SyncSong>,
        override val downloadingSongs: List<SyncSong>,
        override val pendingSongsCount: Int,
        override val errorSongsCount: Int,
        override val isDownloading: Boolean,
        override val isUserPaused: Boolean,
        override val pauseReason: PauseReason?
    ) : AlbumDownloadItem() {
        @Composable
        override fun DetailContent(modifier: Modifier) {
            Column(modifier = modifier) {
                if (activeSongs.isNotEmpty()) {
                    val first = activeSongs.first()
                    val songStatusText = when {
                        first.isUserPaused -> "用户已暂停"
                        first.pauseReason == PauseReason.METERED_NETWORK -> "流量已暂停"
                        first.pauseReason == PauseReason.THREAD_LIMIT -> "排队中"
                        first.errorMessage != null -> "错误: ${first.errorMessage}"
                        else -> "${first.downloadProgress}%"
                    }
                    SongProgressItem(
                        title = "Disc Audio File",
                        size = first.size,
                        downloadedBytes = first.downloadedBytes,
                        progress = first.downloadProgress,
                        statusText = songStatusText
                    )
                }
                SummaryInfo()
            }
        }
    }

    @Composable
    protected fun SummaryInfo() {
        if (errorSongsCount > 0) {
            Text(
                text = "$errorSongsCount 首曲目下载失败",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (pendingSongsCount > 0) {
            Text(
                text = "$pendingSongsCount 首曲目等待中...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    protected fun SongProgressItem(
        title: String,
        size: Long,
        downloadedBytes: Long,
        progress: Int,
        statusText: String
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
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
                        text = "${Utils.formatSize(downloadedBytes)} / ${Utils.formatSize(size)}",
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
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
