package md.oak.sonark.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.screens.AlbumScreen
import md.oak.sonark.ui.screens.ArtistScreen
import md.oak.sonark.ui.screens.HomeScreen
import md.oak.sonark.ui.screens.LibraryScreen
import md.oak.sonark.ui.screens.PlayerScreen
import md.oak.sonark.ui.screens.SearchScreen
import md.oak.sonark.ui.screens.SettingsScreen

import android.util.Log

@androidx.media3.common.util.UnstableApi
@Composable
fun createNavEntryProvider(
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    navigator: Navigator,
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<HomeKey> {
            HomeScreen()
        }
        entry<LibraryKey> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            val artists by viewModel.artists.collectAsStateWithLifecycle()
            val songs by viewModel.songs.collectAsStateWithLifecycle()
            
            val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
            val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
            val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

            LibraryScreen(
                uiState = uiState,
                albums = albums,
                artists = artists,
                songs = songs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                onAlbumClick = { album ->
                    navigator.navigate(AlbumKey(album.id))
                },
                onArtistClick = { artist ->
                    navigator.navigate(ArtistKey(artist.name))
                },
                onSongClick = { syncSong ->
                    Log.e("NavGraph", "onSongClick: ${syncSong.song.title}")
                    if (syncSong.song.id == currentSong?.song?.id) {
                        navigator.navigate(PlayerKey)
                    } else {
                        playbackViewModel.playQueue(songs, songs.indexOf(syncSong))
                    }
                },
                onRefresh = { viewModel.loadSongs() },
            )
        }
        entry<SearchKey> {
            val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
            val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
            val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

            SearchScreen(
                viewModel = searchViewModel,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                onBackClick = { navigator.goBack() },
                onSongClick = { song ->
                    playbackViewModel.playQueue(listOf(song), 0)
                },
                onAlbumClick = { album ->
                    navigator.navigate(AlbumKey(album.id))
                },
                onArtistClick = { artistName ->
                    navigator.navigate(ArtistKey(artistName))
                },
            )
        }
        entry<ArtistKey> { key ->
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            
            val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
            val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
            val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

            val personalAlbums = remember(key, albums) {
                albums.filter { it.artist == key.artistName }
            }
            
            val featuredSongsList = remember(key) {
                viewModel.getSongsForArtist(key.artistName)
            }

            ArtistScreen(
                artistName = key.artistName,
                personalAlbums = personalAlbums,
                featuredSongs = featuredSongsList,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                onAlbumClick = { album ->
                    navigator.navigate(AlbumKey(album.id))
                },
                onSongClick = { syncSong ->
                    playbackViewModel.playQueue(featuredSongsList, featuredSongsList.indexOf(syncSong))
                },
                onBackClick = { navigator.goBack() },
                onLoadMetadata = { songsToLoad -> viewModel.fetchMetadataForSongs(songsToLoad) },
                onDownloadSongs = viewModel::downloadSongs,
            )
        }
        entry<AlbumKey> { key ->
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            
            val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
            val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
            val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

            val album = remember(key, albums) {
                albums.find { it.id == key.albumId }
            }
            val albumSongs = remember(key) {
                viewModel.getSongsForAlbum(key.albumId)
            }
            album?.let {
                AlbumScreen(
                    album = it,
                    songs = albumSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                    onSongClick = { song ->
                        if (song.song.id == currentSong?.song?.id) {
                            navigator.navigate(PlayerKey)
                        } else {
                            playbackViewModel.playQueue(albumSongs, albumSongs.indexOf(song))
                        }
                    },
                    onBackClick = { navigator.navigate(LibraryKey) },
                    onLoadMetadata = { songsToLoad -> viewModel.fetchMetadataForSongs(songsToLoad) },
                    onDownloadSongs = viewModel::downloadSongs,
                )
            }
        }
        entry<PlayerKey> {
            val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
            val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
            val duration by playbackViewModel.duration.collectAsStateWithLifecycle()
            val shuffleEnabled by playbackViewModel.shuffleEnabled.collectAsStateWithLifecycle()
            val repeatMode by playbackViewModel.repeatMode.collectAsStateWithLifecycle()

            PlayerScreen(
                song = currentSong,
                isPlaying = isPlaying,
                progress = playbackProgress,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onTogglePlayback = { playbackViewModel.togglePlayback() },
                onSeekTo = { playbackViewModel.seekTo(it) },
                onSkipNext = { playbackViewModel.skipNext() },
                onSkipPrevious = { playbackViewModel.skipPrevious() },
                onToggleShuffle = { playbackViewModel.toggleShuffle() },
                onToggleRepeatMode = { playbackViewModel.toggleRepeatMode() },
                onBackClick = { navigator.navigate(LibraryKey) },
                onAlbumClick = { albumId ->
                    navigator.navigate(AlbumKey(albumId))
                },
            )
        }
        entry<SettingsKey> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = { navigator.goBack() },
            )
        }
    }
}
