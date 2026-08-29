package md.oak.sonark.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import md.oak.sonark.data.AccountSession
import md.oak.sonark.data.SessionManager
import md.oak.sonark.data.database.AlbumEntity
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.provider.MusicProvider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private fun <T> Flow<AccountSession?>.withSession(
    transform: (AccountSession) -> Flow<List<T>>
): Flow<List<T>> = flatMapLatest { session ->
    if (session == null) flowOf(emptyList()) else transform(session)
}

class MusicRepository(
    context: Context,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val metadataManager: MetadataManager = MetadataManager(context, sessionManager)
) {

    private val providers = mutableMapOf<String, MusicProvider>()

    fun registerProvider(provider: MusicProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): MusicProvider? = providers[id]

    /**
     * Returns a flow of SyncSongs, which include both metadata and synchronization state.
     */
    fun getSyncSongsFlow(): Flow<List<SyncSong>> = sessionManager.currentSession.withSession { session ->
        session.albumDao.getAlbumsWithSongsFlow().map { albumWithSongs ->
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
    fun getSongsFlow(): Flow<List<Song>> = sessionManager.currentSession.withSession { session ->
        session.albumDao.getAlbumsWithSongsFlow().map { albumWithSongs ->
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

    fun getAlbumsFlow(): Flow<List<Album>> = sessionManager.currentSession.withSession { session ->
        session.albumDao.getAlbumsWithSongsFlow().map { entities ->
            entities.map { it.toAlbum() }
        }
    }

    suspend fun syncAll() {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            val songDao = session.songDao
            val albumDao = session.albumDao

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
    }

    private fun createAlbumEntity(albumId: String, syncSongs: List<SyncSong>, existing: AlbumEntity?): AlbumEntity {
        // ... (rest of the private methods are same as before as they don't depend on DAOs directly except createSongEntity which I'll check)
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
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.resumeDownload(songId) // Use resume logic to set to PENDING
        }
    }

    suspend fun pauseDownload(songId: String) {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.pauseDownload(songId)
        }
    }

    suspend fun pauseAlbumDownload(albumId: String) {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.pauseDownloadsByAlbum(albumId)
        }
    }

    suspend fun resumeDownload(songId: String) {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.resumeDownload(songId)
        }
    }

    suspend fun resumeAlbumDownload(albumId: String) {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.resumeDownloadsByAlbum(albumId)
        }
    }

    suspend fun pauseAllDownloads() {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.pauseAllDownloads()
        }
    }

    suspend fun resumeAllDownloads() {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            session.songDao.resumeAllDownloads()
        }
    }

    suspend fun fetchMetadata(songId: String) {
        metadataManager.fetchMetadata(songId)
    }
}
