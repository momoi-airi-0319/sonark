package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.data.database.SonarkDatabase
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var database: SonarkDatabase

    fun init(context: Context) {
        if (!::database.isInitialized) {
            database = SonarkDatabase.getDatabase(context.applicationContext)
        }
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository(context.applicationContext, database.songDao())
        }
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
    }
}
