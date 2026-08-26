package md.oak.sonark.data.model

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    NONE, PENDING, DOWNLOADING, COMPLETED, ERROR
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
    val imageUrl: String? = null,
    val type: AlbumType = AlbumType.NORMAL
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
    val downloadProgress: Int = 0
)
