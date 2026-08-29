package md.oak.sonark.data

import kotlinx.coroutines.CoroutineScope
import md.oak.sonark.data.database.AlbumDao
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.database.SongDao
import java.io.File

data class AccountSession(
    val email: String,
    val database: SonarkDatabase,
    val songDao: SongDao,
    val albumDao: AlbumDao,
    val musicDir: File,
    val scope: CoroutineScope
)
