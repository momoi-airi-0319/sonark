package md.oak.sonark.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val data: String, // Drive File ID or Download URL
    val albumId: String,
    val imageUrl: String? = null,
    val localPath: String? = null,
    val isCueAlbum: Boolean = false,
    val startOffset: Long = 0L
)

data class Album(
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val songs: List<Song>,
    val isCueAlbum: Boolean = false
)
