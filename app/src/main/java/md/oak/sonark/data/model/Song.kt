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
    val imageUrl: String? = null
)

data class Album(
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val songs: List<Song>
)
