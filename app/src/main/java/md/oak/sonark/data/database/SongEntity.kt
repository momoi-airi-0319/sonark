package md.oak.sonark.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import md.oak.sonark.data.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val data: String,
    val albumId: String,
    val imageUrl: String?,
    val localPath: String?,
    val isCueAlbum: Boolean,
    val startOffset: Long
) {
    fun toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        data = data,
        albumId = albumId,
        imageUrl = imageUrl,
        localPath = localPath,
        isCueAlbum = isCueAlbum,
        startOffset = startOffset
    )

    companion object {
        fun fromSong(song: Song) = SongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            data = song.data,
            albumId = song.albumId,
            imageUrl = song.imageUrl,
            localPath = song.localPath,
            isCueAlbum = song.isCueAlbum,
            startOffset = song.startOffset
        )
    }
}
