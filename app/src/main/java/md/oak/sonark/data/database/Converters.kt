package md.oak.sonark.data.database

import androidx.room.TypeConverter
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus

class Converters {
    @TypeConverter
    fun fromAlbumType(value: AlbumType): String = value.name

    @TypeConverter
    fun toAlbumType(value: String): AlbumType = AlbumType.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
