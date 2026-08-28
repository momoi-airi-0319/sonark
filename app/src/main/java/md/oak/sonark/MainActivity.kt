package md.oak.sonark

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch
import md.oak.sonark.data.Dependencies
import md.oak.sonark.navigation.AlbumKey
import md.oak.sonark.navigation.ArtistKey
import md.oak.sonark.navigation.LibraryKey
import md.oak.sonark.navigation.Navigator
import md.oak.sonark.navigation.PlayerKey
import md.oak.sonark.navigation.SearchKey
import md.oak.sonark.navigation.SettingsKey
import md.oak.sonark.navigation.rememberNavigationState
import md.oak.sonark.navigation.toEntries
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.components.DownloadQueueBottomSheet
import md.oak.sonark.ui.components.FloatingNavItem
import md.oak.sonark.ui.screens.AlbumScreen
import md.oak.sonark.ui.screens.LibraryScreen
import md.oak.sonark.ui.screens.PlayerScreen
import md.oak.sonark.ui.screens.SearchScreen
import md.oak.sonark.ui.screens.SettingsScreen
import md.oak.sonark.ui.theme.SonarkTheme

class MainActivity : ComponentActivity() {

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Dependencies.init(applicationContext)
        val repository = Dependencies.musicRepository
        val settingsRepository = Dependencies.settingsRepository

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(Interceptor { chain ->
                    val request = chain.request
                    val url = request.data.toString()
                    if (url.contains("googleapis.com") || url.contains("drive.google.com")) {
                        val token = runCatching { 
                            Dependencies.driveProvider.credential?.getToken() 
                        }.getOrNull()
                        
                        if (token != null) {
                            val authenticatedRequest = request.newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            return@Interceptor chain.proceed(authenticatedRequest)
                        }
                    }
                    chain.proceed(request)
                })
            }
            .build()
        Coil.setImageLoader(imageLoader)

        fun updateDriveService(account: GoogleSignInAccount?) {
            if (account != null) {
                try {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        this, listOf(DriveScopes.DRIVE_READONLY)
                    ).setSelectedAccount(account.account)
                    
                    Dependencies.driveProvider.credential = credential
                } catch (e: Exception) {
                    Log.e("Sonark", "Error updating drive service", e)
                    Dependencies.driveProvider.credential = null
                }
            } else {
                Dependencies.driveProvider.credential = null
            }
        }

        setContent {
            SonarkTheme {
                val viewModel: MainViewModel = viewModel { 
                    MainViewModel(repository, Dependencies.accountRepository) 
                }
                val searchViewModel: SearchViewModel = viewModel { SearchViewModel(repository) }
                val playbackViewModel: PlaybackViewModel = viewModel { PlaybackViewModel(application) }
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
                
                val scope = rememberCoroutineScope()
                
                val gso = remember {
                    @Suppress("DEPRECATION")
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
                        .build()
                }
                val googleSignInClient = remember { 
                    @Suppress("DEPRECATION")
                    GoogleSignIn.getClient(this@MainActivity, gso) 
                }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        @Suppress("DEPRECATION")
                        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            val account = task.getResult(ApiException::class.java)
                            settingsViewModel.setGoogleAccount(account?.email)
                            updateDriveService(account)
                            viewModel.loadSongs()
                        } catch (e: ApiException) {
                            Log.e("Sonark", "Sign-in failed with status code: ${e.statusCode}")
                            Toast.makeText(this@MainActivity, "Error Code: ${e.statusCode}", Toast.LENGTH_LONG).show()
                            settingsViewModel.setGoogleAccount(null)
                            updateDriveService(null)
                            viewModel.setUnauthenticated()
                        }
                    }
                }

                val songs by viewModel.songs.collectAsStateWithLifecycle()
                val albums by viewModel.albums.collectAsStateWithLifecycle()
                val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
                val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
                
                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                val storageQuota by viewModel.storageQuota.collectAsStateWithLifecycle()
                val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
                val googleAccountName by settingsViewModel.googleAccountName.collectAsStateWithLifecycle()

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
                
                var showQueue by remember { mutableStateOf(false) }

                val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
                val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
                val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
                val duration by playbackViewModel.duration.collectAsStateWithLifecycle()
                val shuffleEnabled by playbackViewModel.shuffleEnabled.collectAsStateWithLifecycle()
                val repeatMode by playbackViewModel.repeatMode.collectAsStateWithLifecycle()

                val navigationState = rememberNavigationState(
                    startRoute = LibraryKey,
                    topLevelRoutes = setOf(LibraryKey, SettingsKey)
                )
                val navigator = remember { Navigator(navigationState) }

                LaunchedEffect(Unit) {
                    @Suppress("DEPRECATION")
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                    if (lastAccount != null) {
                        settingsViewModel.setGoogleAccount(lastAccount.email)
                        updateDriveService(lastAccount)
                        viewModel.loadSongs()
                    } else {
                        viewModel.setUnauthenticated()
                    }
                }

                LaunchedEffect(googleAccountName) {
                    if (googleAccountName != null) {
                        viewModel.refreshAccountInfo()
                    }
                }

                val myEntryProvider = entryProvider {
                    entry<LibraryKey> {
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        LibraryScreen(
                            uiState = uiState,
                            albums = albums,
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
                            onPlayerClick = { navigator.navigate(PlayerKey) },
                            onRefresh = { viewModel.loadSongs() },
                            onQueueClick = { showQueue = true },
                            onSettingsClick = { navigator.navigate(SettingsKey) },
                            onAddAccountClick = {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            },
                            onManageAccountsClick = {
                                Toast.makeText(this@MainActivity, "Manage Accounts not implemented", Toast.LENGTH_SHORT).show()
                            },
                            onGuestModeClick = {
                                scope.launch {
                                    repository.setGuestMode(true)
                                    viewModel.loadSongs()
                                }
                            },
                            onSignOutClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    settingsViewModel.setGoogleAccount(null)
                                    updateDriveService(null)
                                    viewModel.loadSongs()
                                }
                            },
                            onAccountClick = { account ->
                                settingsViewModel.setGoogleAccount(account.email)
                                viewModel.loadSongs()
                            },
                            onUrlClick = { url ->
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                startActivity(intent)
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
                        val artistAlbums = remember(key, albums) {
                            albums.filter { it.artist == key.artistName }
                        }
                        md.oak.sonark.ui.screens.ArtistScreen(
                            artistName = key.artistName,
                            albums = artistAlbums,
                            onAlbumClick = { album ->
                                navigator.navigate(AlbumKey(album.title))
                            },
                            onBackClick = { navigator.goBack() }
                        )
                    }
                    entry<AlbumKey> { key ->
                        val currentSongs = songs
                        val album = remember(key, albums) {
                            albums.find { it.title == key.albumTitle }
                        }
                        val albumSongs = remember(key, currentSongs) { 
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

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute]
                        val isAtTopLevelRoot = (currentBackStack?.size ?: 0) <= 1
                        
                        if (isAtTopLevelRoot && navigationState.topLevelRoute != SettingsKey) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    tonalElevation = 6.dp,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FloatingNavItem(
                                            selected = navigationState.topLevelRoute == LibraryKey,
                                            onClick = { navigator.navigate(LibraryKey) },
                                            icon = Icons.Default.LibraryMusic,
                                            label = "Library"
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Surface(
                                    shape = CircleShape,
                                    tonalElevation = 6.dp,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        IconButton(
                                            onClick = { navigator.navigate(SearchKey) },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        NavDisplay(
                            entries = navigationState.toEntries(myEntryProvider),
                            onBack = { navigator.goBack() }
                        )
                    }

                    if (showQueue) {
                        DownloadQueueBottomSheet(
                            queue = downloadQueue,
                            onDismissRequest = { showQueue = false }
                        )
                    }
                }
            }
        }
    }
}
