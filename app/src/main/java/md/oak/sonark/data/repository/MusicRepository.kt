package md.oak.sonark.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import md.oak.sonark.data.database.SongDao
import md.oak.sonark.data.database.SongEntity
import md.oak.sonark.data.model.Song
import md.oak.sonark.data.provider.MusicProvider
import java.io.FileOutputStream

class MusicRepository(context: Context, private val songDao: SongDao) {

    private val cacheDir = java.io.File(context.cacheDir, "music_cache").apply { if (!exists()) mkdirs() }
    private val providers = mutableMapOf<String, MusicProvider>()

    fun registerProvider(provider: MusicProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): MusicProvider? = providers[id]

    fun getSongsFlow(): Flow<List<Song>> {
        return songDao.getAllSongsFlow().map { entities ->
            entities.map { it.toSong() }
        }
    }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        val allSongs = mutableListOf<Song>()
        for (provider in providers.values) {
            try {
                allSongs.addAll(provider.syncLibrary())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (allSongs.isNotEmpty()) {
            val entities = allSongs.map { SongEntity.fromSong(it) }
            songDao.insertSongs(entities)
            songDao.deleteSongsNotIn(entities.map { it.id })
        }
    }

    suspend fun downloadSong(song: Song): String? = withContext(Dispatchers.IO) {
        val provider = providers[song.providerId] ?: return@withContext null
        
        // Use a stable ID for caching, e.g., the part of data URL or just song ID
        // For Drive, we know it's the fileId, but for others it might differ.
        // Let's use a hash or provider-specific logic if needed.
        // For now, let's stick to a simple cache key.
        val cacheKey = song.id.replace("/", "_").replace(":", "_")
        val localFile = java.io.File(cacheDir, cacheKey)
        
        if (localFile.exists()) return@withContext localFile.absolutePath

        try {
            if (provider.downloadSong(song, localFile)) {
                val path = localFile.absolutePath
                
                // Update database for this song
                val entity = songDao.getSongById(song.id)
                if (entity != null) {
                    songDao.updateSong(entity.copy(localPath = path))
                }
                path
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchMetadata(songId: String) = withContext(Dispatchers.IO) {
        val entity = songDao.getSongById(songId) ?: return@withContext
        if (entity.localPath == null) return@withContext
        
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entity.localPath)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: entity.title
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            var imageUrl = entity.imageUrl
            if (imageUrl == null || imageUrl.startsWith("https://")) {
                val art = retriever.embeddedPicture
                if (art != null) {
                    val artFile = java.io.File(cacheDir, "art_${entity.id}.jpg")
                    if (!artFile.exists()) {
                        FileOutputStream(artFile).use { it.write(art) }
                    }
                    imageUrl = artFile.absolutePath
                }
            }
            
            songDao.updateSong(entity.copy(
                artist = artist, 
                title = title, 
                duration = duration, 
                imageUrl = imageUrl
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
    }
}
