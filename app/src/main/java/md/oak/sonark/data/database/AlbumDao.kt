package md.oak.sonark.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Transaction
    @Query("SELECT * FROM albums")
    fun getAlbumsWithSongsFlow(): Flow<List<AlbumWithSongs>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteAlbumsNotIn(ids: List<String>)
}
