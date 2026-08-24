package md.oak.sonark.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import md.oak.sonark.data.model.DownloadStatus

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE downloadStatus = 'PENDING' OR downloadStatus = 'DOWNLOADING'")
    fun getSongsToDownloadFlow(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET downloadStatus = :status, downloadProgress = :progress WHERE data = :dataUrl")
    suspend fun updateDownloadStatusByUrl(dataUrl: String, status: DownloadStatus, progress: Int)

    @Query("UPDATE songs SET localPath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100 WHERE data = :dataUrl")
    suspend fun markUrlAsDownloaded(dataUrl: String, path: String)

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE id = :id")
    suspend fun resetDownloadStatus(id: String)

    @Query("SELECT * FROM songs WHERE downloadStatus != 'COMPLETED' AND downloadStatus != 'NONE'")
    fun getDownloadQueueFlow(): Flow<List<SongEntity>>

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotIn(ids: List<String>)
}
