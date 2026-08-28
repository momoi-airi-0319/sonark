package md.oak.sonark.navigation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.screens.AlbumScreen
import md.oak.sonark.ui.screens.ArtistScreen
import md.oak.sonark.ui.screens.LibraryScreen
import md.oak.sonark.ui.screens.PlayerScreen
import md.oak.sonark.ui.screens.SearchScreen
import md.oak.sonark.ui.screens.SettingsScreen
import androidx.core.net.toUri
import md.oak.sonark.ui.utils.ArtistUtils

@Composable
fun createNavEntryProvider(
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    navigator: Navigator,
    authManager: AuthManager,
    googleSignInLauncher: ActivityResultLauncher<Intent>,
    context: Context,
    onShowQueue: () -> Unit
): (NavKey) -> NavEntry<NavKey> {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val storageQuota by viewModel.storageQuota.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    val googleAccountName by settingsViewModel.googleAccountName.collectAsStateWithLifecycle()

    val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
    val duration by playbackViewModel.duration.collectAsStateWithLifecycle()
    val shuffleEnabled by playbackViewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by playbackViewModel.repeatMode.collectAsStateWithLifecycle()

    val activeAccount = remember(accounts, googleAccountName) {
        if (googleAccountName == null) null
        else accounts.find { it.email.equals(googleAccountName, ignoreCase = true) }
            ?: UserAccount(
                name = googleAccountName?.split("@")?.firstOrNull() ?: "User",
                email = googleAccountName!!
            )
    }
    val otherAccounts = remember(accounts, activeAccount) {
        accounts.filter { it.email != activeAccount?.email }
    }
    val queueSize = remember(downloadQueue) {
        downloadQueue.sumOf { it.totalSongs }
    }

    return entryProvider {
        entry<LibraryKey> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = uiState,
                albums = albums,
                artists = artists,
                songs = songs,
                activeAccount = activeAccount,
                otherAccounts = otherAccounts,
                storageQuota = storageQuota,
                isGuestMode = isGuestMode,
                downloadQueueSize = queueSize,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                sortOrder = sortOrder,
                onSortOrderChange = { viewModel.setSortOrder(it) },
                onAlbumClick = { album ->
                    navigator.navigate(AlbumKey(album.title))
                },
                onArtistClick = { artist ->
                    navigator.navigate(ArtistKey(artist.name))
                },
                onSongClick = { syncSong ->
                    if (syncSong.song.id == currentSong?.song?.id) {
                        navigator.navigate(PlayerKey)
                    } else {
                        playbackViewModel.playQueue(songs, songs.indexOf(syncSong))
                    }
                },
                onPlayerClick = { navigator.navigate(PlayerKey) },
                onRefresh = { viewModel.loadSongs() },
                onQueueClick = onShowQueue,
                onSettingsClick = { navigator.navigate(SettingsKey) },
                onAddAccountClick = {
                    googleSignInLauncher.launch(authManager.googleSignInClient.signInIntent)
                },
                onManageAccountsClick = {
                    Toast.makeText(context, "Manage Accounts not implemented", Toast.LENGTH_SHORT).show()
                },
                onGuestModeClick = {
                    viewModel.setGuestMode(true)
                },
                onSignOutClick = {
                    authManager.signOut {
                        settingsViewModel.setGoogleAccount(null)
                        viewModel.loadSongs()
                    }
                },
                onAccountClick = { account ->
                    settingsViewModel.setGoogleAccount(account.email)
                    viewModel.loadSongs()
                },
                onUrlClick = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                }
            )
        }
        entry<SearchKey> {
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
                    navigator.navigate(AlbumKey(album.title))
                },
                onArtistClick = { artistName ->
                    navigator.navigate(ArtistKey(artistName))
                }
            )
        }
        entry<ArtistKey> { key ->
            val normalizedTarget = remember(key) { ArtistUtils.normalize(key.artistName) }
            
            val personalAlbums = remember(normalizedTarget, albums) {
                albums.filter { ArtistUtils.normalize(it.artist) == normalizedTarget }
            }
            
            val featuredSongs = remember(normalizedTarget, songs) {
                songs.filter { syncSong ->
                    // Artist is in song's artist list
                    val isParticipant = ArtistUtils.splitArtists(syncSong.song.artist).any { ArtistUtils.normalize(it) == normalizedTarget }
                    // AND it's NOT part of a personal album (to avoid duplicates)
                    val isInPersonalAlbum = personalAlbums.any { it.title == syncSong.song.album }
                    isParticipant && !isInPersonalAlbum
                }
            }

            ArtistScreen(
                artistName = key.artistName,
                personalAlbums = personalAlbums,
                featuredSongs = featuredSongs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                onAlbumClick = { album ->
                    navigator.navigate(AlbumKey(album.title))
                },
                onSongClick = { syncSong ->
                    playbackViewModel.playQueue(featuredSongs, featuredSongs.indexOf(syncSong))
                },
                onBackClick = { navigator.goBack() },
                onLoadMetadata = { viewModel.fetchMetadataForSongs(it) },
                onDownloadSongs = { viewModel.downloadSongs(it) }
            )
        }
        entry<AlbumKey> { key ->
            val album = remember(key, albums) {
                albums.find { it.title == key.albumTitle }
            }
            val albumSongs = remember(key, songs) {
                viewModel.getSongsForAlbum(key.albumTitle)
            }
            if (album != null) {
                AlbumScreen(
                    album = album,
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
                    onLoadMetadata = { viewModel.fetchMetadataForSongs(it) },
                    onDownloadSongs = { viewModel.downloadSongs(it) }
                )
            }
        }
        entry<PlayerKey> {
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
                onAlbumClick = { albumTitle ->
                    navigator.navigate(AlbumKey(albumTitle))
                }
            )
        }
        entry<SettingsKey> {
            SettingsScreen(
                onBackClick = { navigator.goBack() }
            )
        }
    }
}
