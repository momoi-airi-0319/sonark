package md.oak.sonark.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import md.oak.sonark.data.database.AlbumDao
import md.oak.sonark.data.database.AlbumEntity
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.AlbumType
import java.io.File
import java.io.FileOutputStream

class MetadataManager(
    context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao
) {
    private val cacheDir = File(context.cacheDir, "music_cache").apply { if (!exists()) mkdirs() }

    suspend fun fetchMetadata(songId: String) = withContext(Dispatchers.IO) {
        val entity = songDao.getSongById(songId) ?: throw IllegalArgumentException("Song not found: $songId")
        val localPath = entity.localPath ?: return@withContext // Nothing to fetch if not downloaded
        
        val album = albumDao.getAlbumById(entity.albumId) ?: throw IllegalStateException("Album not found for song: $songId")

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(localPath)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entity.title
            val totalDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            updateAlbumArt(retriever, album, entity.id)

            if (album.type == AlbumType.CUE) {
                updateCueSongsDuration(album.id, totalDuration)
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

    private suspend fun updateAlbumArt(retriever: MediaMetadataRetriever, album: AlbumEntity, songId: String) {
        var imageUrl = album.imageUrl
        if (imageUrl == null || imageUrl.startsWith("https://")) {
            val art = retriever.embeddedPicture
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

    private suspend fun updateCueSongsDuration(albumId: String, totalDuration: Long) {
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
