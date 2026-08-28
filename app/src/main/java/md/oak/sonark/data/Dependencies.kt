package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.download.DownloadManager
import md.oak.sonark.data.provider.DriveMusicProvider
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var accountRepository: AccountRepository
    lateinit var database: SonarkDatabase
    lateinit var downloadManager: DownloadManager
    val driveProvider = DriveMusicProvider()

    fun init(context: Context) {
        if (!::database.isInitialized) {
            database = SonarkDatabase.getDatabase(context.applicationContext)
        }
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(
                context.applicationContext,
                database.songDao(),
                database.albumDao(),
                settingsRepository
            ).apply {
                registerProvider(driveProvider)
            }
        }
        if (!::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                context.applicationContext,
                database.songDao(),
                database.albumDao(),
                musicRepository
            ).apply { start() }
        }
        if (!::accountRepository.isInitialized) {
            accountRepository = AccountRepository()
        }
    }
}
