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
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
import md.oak.sonark.data.auth.DriveAuthHolder
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository
import md.oak.sonark.navigation.LibraryKey
import md.oak.sonark.navigation.Navigator
import md.oak.sonark.navigation.PlayerKey
import md.oak.sonark.navigation.SettingsKey
import md.oak.sonark.navigation.rememberNavigationState
import md.oak.sonark.navigation.toEntries
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SettingsViewModel
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

        fun updateDriveService(account: GoogleSignInAccount?) {
            if (account != null) {
                try {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        this, listOf(DriveScopes.DRIVE_READONLY)
                    ).setSelectedAccount(account.account)
                    
                    DriveAuthHolder.credential = credential
                    
                    val driveService = Drive.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("Sonark").build()
                    
                    repository.setDriveService(driveService)
                    Log.d("Sonark", "Drive service updated for ${account.email}")
                } catch (e: Exception) {
                    Log.e("Sonark", "Error updating drive service", e)
                    DriveAuthHolder.credential = null
                    repository.setDriveService(null)
                }
            } else {
                DriveAuthHolder.credential = null
                repository.setDriveService(null)
                Log.d("Sonark", "Drive service cleared")
            }
        }

        setContent {
            SonarkTheme {
                val viewModel: MainViewModel = viewModel { MainViewModel(repository, settingsRepository) }
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
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
                
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
                                val albums by viewModel.albums.collectAsStateWithLifecycle()
                                LibraryScreen(
                                    uiState = uiState,
                                    albums = albums,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    sortOrder = sortOrder,
                                    onSortOrderChange = { viewModel.setSortOrder(it) },
                                    onAlbumClick = { album ->
                                        playbackViewModel.playQueue(album.songs, 0)
                                        navigator.navigate(PlayerKey)
                                    },
                                    onSongClick = { song ->
                                        playbackViewModel.playQueue(songs, songs.indexOf(song))
                                        navigator.navigate(PlayerKey)
                                    },
                                    onRefresh = { viewModel.loadSongs() }
                                )
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
                }
            }
        }
    }
}
