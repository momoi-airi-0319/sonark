package md.oak.sonark.data.provider

import android.net.Uri
import md.oak.sonark.data.model.SyncSong
import java.io.File

interface MusicProvider {
    val id: String
    val name: String
    
    suspend fun syncLibrary(): List<SyncSong>
    suspend fun resolveStreamUri(song: SyncSong): Uri
    suspend fun downloadSong(
        song: SyncSong, 
        targetFile: File, 
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean
    fun getAuthHeaders(): Map<String, String>
}
