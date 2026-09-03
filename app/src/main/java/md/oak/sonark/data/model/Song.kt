package md.oak.sonark.data.model

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    NONE, PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR
}

enum class PauseReason {
    USER_PAUSED,       // 用户手动暂停
    METERED_NETWORK,   // 流量计费网络自动暂停
    THREAD_LIMIT       // 达到最大线程数等待中
}

/**
 * Ideal model for a song, containing only core metadata.
 */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val imageUrl: String? = null,
    val type: AlbumType = AlbumType.NORMAL
)

data class Disc(
    val discNumber: Int,
    val songs: List<Song>
)

/**
 * Sync model for a song, containing core metadata plus sync and local state.
 */
@Serializable
data class SyncSong(
    val song: Song,
    val data: String, // Drive File ID or Download URL
    val albumId: String,
    val providerId: String = "",
    val size: Long = 0,
    val md5Hash: String? = null,
    val localPath: String? = null,
    val startOffset: Long = 0L,
    val coverData: String? = null,
    val coverSize: Long = 0,
    val coverMd5: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0,
    val isUserPaused: Boolean = false,
    val pauseReason: PauseReason? = null,
    val errorMessage: String? = null
)
