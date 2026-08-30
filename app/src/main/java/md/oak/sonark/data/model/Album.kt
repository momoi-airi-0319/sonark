package md.oak.sonark.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlbumType {
    NORMAL,
    CUE
}

@Serializable
sealed class Album {
    abstract val id: String
    abstract val title: String
    abstract val artist: String
    abstract val imageUrl: String?
    abstract val localPath: String?
    abstract val songs: List<Song>
    abstract val type: AlbumType

    data class Normal(
        override val id: String,
        override val title: String,
        override val artist: String,
        override val imageUrl: String?,
        override val localPath: String? = null,
        override val songs: List<Song> = emptyList()
    ) : Album() {
        override val type: AlbumType = AlbumType.NORMAL
    }

    data class Cue(
        override val id: String,
        override val title: String,
        override val artist: String,
        override val imageUrl: String?,
        override val localPath: String? = null,
        override val songs: List<Song> = emptyList()
    ) : Album() {
        override val type: AlbumType = AlbumType.CUE
    }
}
