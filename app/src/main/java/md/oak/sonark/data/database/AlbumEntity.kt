package md.oak.sonark.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import md.oak.sonark.data.model.AlbumType

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val localPath: String? = null,
    val downloadStatus: md.oak.sonark.data.model.DownloadStatus = md.oak.sonark.data.model.DownloadStatus.NONE,
    val downloadProgress: Int = 0,
    val size: Long = 0,
    val md5Hash: String? = null,
    val type: AlbumType
)
