package md.oak.sonark.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Transaction
    @Query("SELECT * FROM albums")
    fun getAlbumsWithSongsFlow(): Flow<List<AlbumWithSongs>>

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE downloadStatus IN ('PENDING', 'ERROR')")
    fun getAlbumsToDownloadFlow(): Flow<List<AlbumEntity>>

    @Query("UPDATE albums SET downloadStatus = 'PENDING' WHERE downloadStatus = 'DOWNLOADING'")
    suspend fun resetAllDownloadingStatus()

    @Query("UPDATE albums SET downloadStatus = :status, downloadProgress = :progress WHERE imageUrl = :url")
    suspend fun updateDownloadStatusByUrl(url: String, status: md.oak.sonark.data.model.DownloadStatus, progress: Int)

    @Query("UPDATE albums SET localPath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100 WHERE imageUrl = :url")
    suspend fun markUrlAsDownloaded(url: String, path: String)

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteAlbumsNotIn(ids: List<String>)
}
