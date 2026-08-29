package md.oak.sonark.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import md.oak.sonark.data.database.AlbumDao
import md.oak.sonark.data.database.AlbumEntity
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.provider.MusicProvider

class MusicRepository(
    context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val settingsRepository: SettingsRepository,
    private val metadataManager: MetadataManager = MetadataManager(context, songDao, albumDao)
) {

    private val providers = mutableMapOf<String, MusicProvider>()

    fun registerProvider(provider: MusicProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): MusicProvider? = providers[id]

    /**
     * Returns a flow of SyncSongs, which include both metadata and synchronization state.
     */
    fun getSyncSongsFlow(): Flow<List<SyncSong>> {
        return albumDao.getAlbumsWithSongsFlow().map { albumWithSongs ->
            albumWithSongs.flatMap { aws ->
                aws.songs.map { songEntity ->
                    songEntity.toSyncSong(
                        albumTitle = aws.album.title,
                        imageUrl = aws.album.imageUrl,
                        type = aws.album.type
                    )
                }
            }
        }
    }

    /**
     * Returns a flow of ideal Songs (metadata only).
     */
    fun getSongsFlow(): Flow<List<Song>> {
        return albumDao.getAlbumsWithSongsFlow().map { albumWithSongs ->
            albumWithSongs.flatMap { aws ->
                aws.songs.map { songEntity ->
                    songEntity.toSong(
                        albumTitle = aws.album.title,
                        imageUrl = aws.album.imageUrl,
                        type = aws.album.type
                    )
                }
            }
        }
    }

    fun getAlbumsFlow(): Flow<List<Album>> {
        return albumDao.getAlbumsWithSongsFlow().map { entities ->
            entities.map { it.toAlbum() }
        }
    }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        val allSyncSongs = providers.values.flatMap { it.syncLibrary() }
        if (allSyncSongs.isEmpty()) return@withContext

        val existingAlbums = albumDao.getAllAlbums().associateBy { it.id }
        val albums = allSyncSongs.groupBy { it.albumId }.map { (albumId, syncSongs) ->
            createAlbumEntity(albumId, syncSongs, existingAlbums[albumId])
        }
        albumDao.insertAlbums(albums)

        val existingSongs = songDao.getAllSongs().associateBy { it.id }
        val songEntities = allSyncSongs.map { syncSong ->
            createSongEntity(syncSong, existingSongs[syncSong.song.id])
        }
        
        songDao.insertSongs(songEntities)

        // Cleanup
        songDao.deleteSongsNotIn(songEntities.map { it.id })
        albumDao.deleteAlbumsNotIn(albums.map { it.id })
    }

    private fun createAlbumEntity(albumId: String, syncSongs: List<SyncSong>, existing: AlbumEntity?): AlbumEntity {
        val firstSyncSong = syncSongs.first()
        val firstSong = firstSyncSong.song

        val isFullyDownloaded = existing != null &&
                               existing.localPath != null &&
                               existing.downloadStatus == DownloadStatus.COMPLETED &&
                               existing.md5Hash == firstSyncSong.coverMd5 &&
                               java.io.File(existing.localPath).exists()

        val isDownloading = existing != null &&
                           existing.downloadStatus == DownloadStatus.DOWNLOADING &&
                           existing.md5Hash == firstSyncSong.coverMd5

        return AlbumEntity(
            id = albumId,
            title = firstSong.album,
            artist = firstSong.artist.takeIf { it != "Unknown Artist" } ?: "Various Artists",
            imageUrl = syncSongs.firstOrNull { it.song.imageUrl != null }?.song?.imageUrl,
            localPath = existing?.localPath,
            downloadStatus = when {
                isFullyDownloaded -> DownloadStatus.COMPLETED
                isDownloading -> DownloadStatus.DOWNLOADING
                firstSyncSong.coverData == null -> DownloadStatus.NONE
                else -> DownloadStatus.PENDING
            },
            downloadProgress = if (isDownloading) existing.downloadProgress else 0,
            size = firstSyncSong.coverSize,
            md5Hash = firstSyncSong.coverMd5,
            type = if (syncSongs.any { it.song.type == AlbumType.CUE }) AlbumType.CUE else AlbumType.NORMAL
        )
    }

    private fun createSongEntity(syncSong: SyncSong, existing: SongEntity?): SongEntity {
        val isFullyDownloaded = existing != null && 
                           existing.localPath != null && 
                           existing.downloadStatus == DownloadStatus.COMPLETED && 
                           existing.md5Hash == syncSong.md5Hash &&
                           java.io.File(existing.localPath).exists()
        
        val isDownloading = existing != null &&
                           existing.downloadStatus == DownloadStatus.DOWNLOADING &&
                           existing.md5Hash == syncSong.md5Hash
        
        val hasValidMetadata = existing != null && existing.artist != "Unknown Artist"

        val finalSyncSong = syncSong.copy(
            song = syncSong.song.copy(
                artist = if (hasValidMetadata) existing.artist else syncSong.song.artist,
                title = if (hasValidMetadata) existing.title else syncSong.song.title,
                duration = if (hasValidMetadata) existing.duration else syncSong.song.duration
            ),
            downloadStatus = when {
                isFullyDownloaded -> DownloadStatus.COMPLETED
                isDownloading -> DownloadStatus.DOWNLOADING
                else -> DownloadStatus.PENDING
            },
            localPath = existing?.localPath,
            downloadProgress = if (isDownloading) existing.downloadProgress else 0
        )
        return SongEntity.fromSyncSong(finalSyncSong)
    }

    suspend fun downloadSong(songId: String) {
        songDao.resetDownloadStatus(songId)
    }

    suspend fun fetchMetadata(songId: String) {
        metadataManager.fetchMetadata(songId)
    }
}
