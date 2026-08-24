package md.oak.sonark.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong

@Entity(
    tableName = "songs",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("albumId")]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val data: String,
    val albumId: String,
    val localPath: String?,
    val startOffset: Long,
    val providerId: String,
    val size: Long = 0,
    val md5Hash: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val downloadProgress: Int = 0
) {
    fun toSong(albumTitle: String, imageUrl: String?, type: AlbumType) = Song(
        id = id,
        title = title,
        artist = artist,
        album = albumTitle,
        duration = duration,
        imageUrl = imageUrl,
        type = type
    )

    fun toSyncSong(albumTitle: String, imageUrl: String?, type: AlbumType) = SyncSong(
        song = toSong(albumTitle, imageUrl, type),
        data = data,
        albumId = albumId,
        localPath = localPath,
        startOffset = startOffset,
        providerId = providerId,
        size = size,
        md5Hash = md5Hash,
        downloadStatus = downloadStatus,
        downloadProgress = downloadProgress
    )

    companion object {
        fun fromSyncSong(syncSong: SyncSong) = SongEntity(
            id = syncSong.song.id,
            title = syncSong.song.title,
            artist = syncSong.song.artist,
            duration = syncSong.song.duration,
            data = syncSong.data,
            albumId = syncSong.albumId,
            localPath = syncSong.localPath,
            startOffset = syncSong.startOffset,
            providerId = syncSong.providerId,
            size = syncSong.size,
            md5Hash = syncSong.md5Hash,
            downloadStatus = syncSong.downloadStatus,
            downloadProgress = syncSong.downloadProgress
        )
    }
}
