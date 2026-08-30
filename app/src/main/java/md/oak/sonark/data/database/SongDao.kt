package md.oak.sonark.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import md.oak.sonark.data.model.DownloadStatus

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId")
    suspend fun getSongsByAlbum(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE downloadStatus IN ('PENDING', 'DOWNLOADING', 'ERROR')")
    fun getSongsToDownloadFlow(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotIn(ids: List<String>)

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE downloadStatus = 'DOWNLOADING'")
    suspend fun resetAllDownloadingStatus()

    @Query("UPDATE songs SET downloadStatus = :status, downloadProgress = :progress, downloadedBytes = :downloadedBytes WHERE data = :dataUrl")
    suspend fun updateDownloadStatusByUrl(dataUrl: String, status: DownloadStatus, progress: Int, downloadedBytes: Long)

    @Query("UPDATE songs SET localPath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100, downloadedBytes = size WHERE data = :dataUrl")
    suspend fun markUrlAsDownloaded(dataUrl: String, path: String)

    @Query("SELECT downloadStatus FROM songs WHERE data = :dataUrl")
    suspend fun getSongStatusByUrl(dataUrl: String): DownloadStatus?

    @Query("UPDATE songs SET downloadStatus = 'PAUSED' WHERE albumId = :albumId AND downloadStatus IN ('PENDING', 'DOWNLOADING', 'ERROR')")
    suspend fun pauseDownloadsByAlbum(albumId: String)

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE albumId = :albumId AND downloadStatus = 'PAUSED'")
    suspend fun resumeDownloadsByAlbum(albumId: String)

    @Query("UPDATE songs SET downloadStatus = 'PAUSED' WHERE id = :id AND downloadStatus IN ('PENDING', 'DOWNLOADING', 'ERROR')")
    suspend fun pauseDownload(id: String)

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE id = :id AND downloadStatus = 'PAUSED'")
    suspend fun resumeDownload(id: String)

    @Query("UPDATE songs SET downloadStatus = 'PAUSED' WHERE downloadStatus IN ('PENDING', 'DOWNLOADING', 'ERROR')")
    suspend fun pauseAllDownloads()

    @Query("UPDATE songs SET downloadStatus = 'PENDING' WHERE downloadStatus = 'PAUSED'")
    suspend fun resumeAllDownloads()
}
