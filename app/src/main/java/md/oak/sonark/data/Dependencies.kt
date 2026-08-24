package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.download.DownloadManager
import md.oak.sonark.data.provider.DriveMusicProvider
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var database: SonarkDatabase
    lateinit var downloadManager: DownloadManager
    val driveProvider = DriveMusicProvider()

    fun init(context: Context) {
        if (!::database.isInitialized) {
            database = SonarkDatabase.getDatabase(context.applicationContext)
        }
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(
                context.applicationContext,
                database.songDao(),
                database.albumDao()
            ).apply {
                registerProvider(driveProvider)
            }
        }
        if (!::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                context.applicationContext,
                database.songDao(),
                musicRepository
            ).apply { start() }
        }
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
    }
}
