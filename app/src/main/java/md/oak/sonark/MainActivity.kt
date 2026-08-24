package md.oak.sonark

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import md.oak.sonark.data.Dependencies
import md.oak.sonark.navigation.AlbumKey
import md.oak.sonark.navigation.LibraryKey
import md.oak.sonark.navigation.Navigator
import md.oak.sonark.navigation.PlayerKey
import md.oak.sonark.navigation.SettingsKey
import md.oak.sonark.navigation.rememberNavigationState
import md.oak.sonark.navigation.toEntries
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.components.DownloadQueueBottomSheet
import md.oak.sonark.ui.screens.AlbumScreen
import md.oak.sonark.ui.screens.LibraryScreen
import md.oak.sonark.ui.screens.PlayerScreen
import md.oak.sonark.ui.screens.SettingsScreen
import md.oak.sonark.ui.theme.SonarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Dependencies.init(applicationContext)
        val repository = Dependencies.musicRepository
        val settingsRepository = Dependencies.settingsRepository

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val request = chain.request
                        val url = request.data.toString()
                        if (url.contains("googleapis.com") || url.contains("drive.google.com")) {
                            val token = withContext(Dispatchers.IO) {
                                try {
                                    Dependencies.driveProvider.credential?.getToken()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (token != null) {
                                val authenticatedRequest = request.newBuilder()
                                    .addHeader("Authorization", "Bearer $token")
                                    .build()
                                return chain.proceed(authenticatedRequest)
                            }
                        }
                        return chain.proceed(request)
                    }
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
                    Log.d("Sonark", "Drive service updated for ${account.email}")
                } catch (e: Exception) {
                    Log.e("Sonark", "Error updating drive service", e)
                    Dependencies.driveProvider.credential = null
                }
            } else {
                Dependencies.driveProvider.credential = null
                Log.d("Sonark", "Drive service cleared")
            }
        }

        setContent {
            SonarkTheme {
                val viewModel: MainViewModel = viewModel { MainViewModel(repository) }
                val playbackViewModel: PlaybackViewModel = viewModel { PlaybackViewModel(application) }
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
                
                val gso = remember {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
                        .build()
                }
                val googleSignInClient = remember { GoogleSignIn.getClient(this@MainActivity, gso) }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
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
                    } else {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            task.getResult(ApiException::class.java)
                        } catch (e: ApiException) {
                            Log.e("Sonark", "Sign-in failed with status code: ${e.statusCode}")
                            Toast.makeText(this@MainActivity, "Error Code: ${e.statusCode}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            val message = if (result.resultCode == Activity.RESULT_CANCELED) "Sign-in cancelled" else "Sign-in failed"
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                        settingsViewModel.setGoogleAccount(null)
                        updateDriveService(null)
                        viewModel.setUnauthenticated()
                    }
                }

                val songs by viewModel.songs.collectAsStateWithLifecycle()
                val albums by viewModel.albums.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
                val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
                
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
                val queue by playbackViewModel.queue.collectAsStateWithLifecycle()

                val navigationState = rememberNavigationState(
                    startRoute = LibraryKey,
                    topLevelRoutes = setOf(LibraryKey, PlayerKey, SettingsKey)
                )
                val navigator = remember { Navigator(navigationState) }

                LaunchedEffect(Unit) {
                    // Check for existing Google account
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                    if (lastAccount != null) {
                        settingsViewModel.setGoogleAccount(lastAccount.email)
                        updateDriveService(lastAccount)
                        viewModel.loadSongs()
                    } else {
                        viewModel.setUnauthenticated()
                    }
                }

                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        item(
                            selected = navigationState.topLevelRoute == LibraryKey,
                            onClick = { navigator.navigate(LibraryKey) },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") }
                        )
                        item(
                            selected = navigationState.topLevelRoute == PlayerKey,
                            onClick = { navigator.navigate(PlayerKey) },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Player") },
                            label = { Text("Player") }
                        )
                        item(
                            selected = navigationState.topLevelRoute == SettingsKey,
                            onClick = { navigator.navigate(SettingsKey) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                ) {
                    val entryProvider = remember {
                        entryProvider {
                            entry<LibraryKey> {
                                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                                LibraryScreen(
                                    uiState = uiState,
                                    albums = albums,
                                    downloadQueueSize = queueSize,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    sortOrder = sortOrder,
                                    onSortOrderChange = { viewModel.setSortOrder(it) },
                                    onAlbumClick = { album ->
                                        navigator.navigate(AlbumKey(album.title))
                                    },
                                    onRefresh = { viewModel.loadSongs() },
                                    onQueueClick = { showQueue = true }
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
                                        onSongClick = { song ->
                                            playbackViewModel.playQueue(albumSongs, albumSongs.indexOf(song))
                                            navigator.navigate(PlayerKey)
                                        },
                                        onBackClick = { navigator.goBack() },
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
                                    queue = queue,
                                    onTogglePlayback = { playbackViewModel.togglePlayback() },
                                    onSeekTo = { playbackViewModel.seekTo(it) },
                                    onSkipNext = { playbackViewModel.skipNext() },
                                    onSkipPrevious = { playbackViewModel.skipPrevious() },
                                    onToggleShuffle = { playbackViewModel.toggleShuffle() },
                                    onToggleRepeatMode = { playbackViewModel.toggleRepeatMode() },
                                    onBackClick = { navigator.goBack() }
                                )
                            }
                            entry<SettingsKey> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onConnectClick = {
                                        Log.e("Sonark", "onConnectClick triggered")
                                        if (settingsViewModel.googleAccountName.value == null) {
                                            Log.e("Sonark", "DEBUG: Launching Sign-In")
                                            Toast.makeText(this@MainActivity, "DEBUG: Launching Sign-In", Toast.LENGTH_SHORT).show()
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        } else {
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                settingsViewModel.setGoogleAccount(null)
                                                updateDriveService(null)
                                                viewModel.loadSongs()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    NavDisplay(
                        entries = navigationState.toEntries(entryProvider),
                        onBack = { navigator.goBack() }
                    )

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
