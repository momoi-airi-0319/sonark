package md.oak.sonark.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
data object LibraryKey : NavKey

@Serializable
data object PlayerKey : NavKey

@Serializable
data object SettingsKey : NavKey
