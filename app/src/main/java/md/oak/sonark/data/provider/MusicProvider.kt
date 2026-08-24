package md.oak.sonark.data.provider

import android.net.Uri
import md.oak.sonark.data.model.Song
import java.io.File

interface MusicProvider {
    val id: String
    val name: String
    
    suspend fun syncLibrary(): List<Song>
    suspend fun resolveStreamUri(song: Song): Uri
    suspend fun downloadSong(song: Song, targetFile: File): Boolean
    fun getAuthHeaders(): Map<String, String>
}
