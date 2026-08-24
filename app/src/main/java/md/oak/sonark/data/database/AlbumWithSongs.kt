package md.oak.sonark.data.database

import androidx.room.Embedded
import androidx.room.Relation
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType

data class AlbumWithSongs(
    @Embedded val album: AlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "albumId",
    )
    val songs: List<SongEntity>
) {
    fun toAlbum(): Album {
        val domainSongs = songs.map { 
            it.toSong(
                albumTitle = album.title,
                imageUrl = album.imageUrl,
                type = album.type
            ) 
        }
        return when (album.type) {
            AlbumType.NORMAL -> Album.Normal(
                title = album.title,
                artist = album.artist,
                imageUrl = album.imageUrl,
                songs = domainSongs
            )
            AlbumType.CUE -> Album.Cue(
                title = album.title,
                artist = album.artist,
                imageUrl = album.imageUrl,
                songs = domainSongs
            )
        }
    }
}
