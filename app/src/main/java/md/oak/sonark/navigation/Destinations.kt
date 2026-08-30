package md.oak.sonark.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
data object HomeKey : NavKey

@Serializable
data object LibraryKey : NavKey

@Serializable
data class AlbumKey(val albumId: String) : NavKey

@Serializable
data object PlayerKey : NavKey

@Serializable
data class ArtistKey(val artistName: String) : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object SettingsKey : NavKey
