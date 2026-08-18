package md.oak.sonark.data

import android.content.Context
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository

object Dependencies {
    lateinit var musicRepository: MusicRepository
    lateinit var settingsRepository: SettingsRepository

    fun init(context: Context) {
        if (!::musicRepository.isInitialized) {
            musicRepository = MusicRepository()
        }
        if (!::settingsRepository.isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
    }
}
