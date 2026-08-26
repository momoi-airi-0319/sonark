package md.oak.sonark.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Log
import md.oak.sonark.data.Utils
import md.oak.sonark.data.database.*
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.provider.MusicProvider
import java.io.FileOutputStream

class MusicRepository(
    context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao
) {

    private val musicDir = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "sonark_music").apply { if (!exists()) mkdirs() }
    private val cacheDir = java.io.File(context.cacheDir, "music_cache").apply { if (!exists()) mkdirs() }
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
        val allSyncSongs = mutableListOf<SyncSong>()
        for (provider in providers.values) {
            try {
                allSyncSongs.addAll(provider.syncLibrary())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (allSyncSongs.isNotEmpty()) {
            val existingAlbums = albumDao.getAllAlbums().associateBy { it.id }
            val albums = allSyncSongs.groupBy { it.albumId }.map { (albumId, syncSongs) ->
                val firstSyncSong = syncSongs.first()
                val firstSong = firstSyncSong.song
                val existing = existingAlbums[albumId]

                val isFullyDownloaded = existing != null &&
                                       existing.localPath != null &&
                                       existing.downloadStatus == DownloadStatus.COMPLETED &&
                                       existing.md5Hash == firstSyncSong.coverMd5 &&
                                       java.io.File(existing.localPath).exists()

                val isDownloading = existing != null &&
                                   existing.downloadStatus == DownloadStatus.DOWNLOADING &&
                                   existing.md5Hash == firstSyncSong.coverMd5

                AlbumEntity(
                    id = albumId,
                    title = firstSong.album,
                    artist = syncSongs.firstOrNull { it.song.artist != "Unknown Artist" }?.song?.artist ?: firstSong.artist,
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
            albumDao.insertAlbums(albums)

            val existingSongs = songDao.getAllSongs().associateBy { it.id }

            val songEntities = allSyncSongs.map { syncSong ->
                val existing = existingSongs[syncSong.song.id]
                
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
                SongEntity.fromSyncSong(finalSyncSong)
            }
            
            songDao.insertSongs(songEntities)

            // Cleanup
            songDao.deleteSongsNotIn(songEntities.map { it.id })
            albumDao.deleteAlbumsNotIn(albums.map { it.id })
        }
    }

    suspend fun downloadSong(songId: String) {
        songDao.resetDownloadStatus(songId)
    }

    suspend fun fetchMetadata(songId: String) = withContext(Dispatchers.IO) {
        val entity = songDao.getSongById(songId) ?: return@withContext
        if (entity.localPath == null) return@withContext
        
        val album = albumDao.getAlbumById(entity.albumId) ?: return@withContext

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entity.localPath)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entity.title
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            var imageUrl = album.imageUrl
            if (imageUrl == null || imageUrl.startsWith("https://")) {
                val art = retriever.embeddedPicture
                if (art != null) {
                    val artFile = java.io.File(cacheDir, "art_${entity.id}.jpg")
                    if (!artFile.exists()) {
                        FileOutputStream(artFile).use { it.write(art) }
                    }
                    imageUrl = artFile.absolutePath
                    albumDao.updateAlbum(album.copy(imageUrl = imageUrl))
                }
            }
            
            songDao.updateSong(entity.copy(
                artist = artist, 
                title = title, 
                duration = duration
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
    }
}
