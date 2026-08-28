package md.oak.sonark.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import md.oak.sonark.data.model.DownloadStatus

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId")
    suspend fun getSongsByAlbum(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotIn(ids: List<String>)

    @Query("SELECT * FROM songs WHERE downloadStatus IN ('PENDING', 'ERROR')")
    fun getSongsToDownloadFlow(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE downloadStatus = 'DOWNLOADING'")
    suspend fun resetAllDownloadingStatus()

    @Query("UPDATE songs SET downloadStatus = :status, downloadProgress = :progress WHERE data = :dataUrl")
    suspend fun updateDownloadStatusByUrl(dataUrl: String, status: DownloadStatus, progress: Int)

    @Query("UPDATE songs SET localPath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100 WHERE data = :dataUrl")
    suspend fun markUrlAsDownloaded(dataUrl: String, path: String)

    @Query("UPDATE songs SET downloadStatus = 'PENDING', downloadProgress = 0 WHERE id = :id")
    suspend fun resetDownloadStatus(id: String)
}
