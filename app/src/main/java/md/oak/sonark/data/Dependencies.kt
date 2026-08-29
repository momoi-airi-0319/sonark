package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.download.DownloadManager
import md.oak.sonark.data.provider.DriveMusicProvider
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MetadataManager
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var accountRepository: AccountRepository
    lateinit var downloadManager: DownloadManager
    lateinit var metadataManager: MetadataManager
    val driveProvider = DriveMusicProvider()

    fun init(context: Context) {
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        SessionManager.init(context.applicationContext, settingsRepository)

        if (!::accountRepository.isInitialized) {
            accountRepository = AccountRepository(settingsRepository)
        }
        if (!::metadataManager.isInitialized) {
            metadataManager = MetadataManager(context.applicationContext, SessionManager)
        }
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(
                context.applicationContext,
                SessionManager,
                settingsRepository,
                metadataManager
            ).apply {
                registerProvider(driveProvider)
            }
        }
        if (!::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                SessionManager,
                musicRepository
            ).apply { start() }
        }
    }
}
