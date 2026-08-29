package md.oak.sonark.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.oak.sonark.data.SessionManager
import md.oak.sonark.data.database.AlbumEntity
import md.oak.sonark.data.model.AlbumType
import java.io.File
import java.io.FileOutputStream

class MetadataManager(
    context: Context,
    private val sessionManager: SessionManager
) {
    private val cacheDir = File(context.cacheDir, "music_cache").apply { if (!exists()) mkdirs() }

    suspend fun fetchMetadata(songId: String) {
        val session = sessionManager.currentSession.value ?: return
        withContext(session.scope.coroutineContext + Dispatchers.IO) {
            val songDao = session.songDao
            val albumDao = session.albumDao

            val entity = songDao.getSongById(songId) ?: run {
                Log.w("MetadataManager", "Song not found: $songId")
                return@withContext
            }
            val localPath = entity.localPath ?: return@withContext // Nothing to fetch if not downloaded
            
            val album = albumDao.getAlbumById(entity.albumId) ?: throw IllegalStateException("Album not found for song: $songId")

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(localPath)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entity.title
                val totalDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                
                updateAlbumArt(retriever, album, entity.id, albumDao)

                if (album.type == AlbumType.CUE) {
                    updateCueSongsDuration(album.id, totalDuration, songDao)
                } else {
                    songDao.updateSong(entity.copy(
                        artist = artist,
                        title = title,
                        duration = totalDuration
                    ))
                }
            } finally {
                retriever.release()
            }
        }
    }

    private suspend fun updateAlbumArt(retriever: MediaMetadataRetriever, album: AlbumEntity, songId: String, albumDao: md.oak.sonark.data.database.AlbumDao) {
        var imageUrl = album.imageUrl
        if (imageUrl == null || imageUrl.startsWith("https://")) {
            val art = try {
                retriever.embeddedPicture
            } catch (e: Exception) {
                Log.e("MetadataManager", "Failed to get embedded picture", e)
                null
            }
            if (art != null) {
                val artFile = File(cacheDir, "art_$songId.jpg")
                if (!artFile.exists()) {
                    FileOutputStream(artFile).use { it.write(art) }
                }
                imageUrl = artFile.absolutePath
                albumDao.updateAlbum(album.copy(imageUrl = imageUrl))
            }
        }
    }

    private suspend fun updateCueSongsDuration(albumId: String, totalDuration: Long, songDao: md.oak.sonark.data.database.SongDao) {
        val cueSongs = songDao.getSongsByAlbum(albumId).sortedBy { it.startOffset }
        for (i in cueSongs.indices) {
            val current = cueSongs[i]
            val trackDuration = if (i < cueSongs.size - 1) {
                cueSongs[i + 1].startOffset - current.startOffset
            } else if (totalDuration > 0) {
                totalDuration - current.startOffset
            } else {
                current.duration
            }
            songDao.updateSong(current.copy(duration = trackDuration))
        }
    }
}
