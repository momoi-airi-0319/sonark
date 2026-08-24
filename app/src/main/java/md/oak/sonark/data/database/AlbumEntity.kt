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
    val type: AlbumType
)
