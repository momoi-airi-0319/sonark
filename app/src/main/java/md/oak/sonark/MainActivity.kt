package md.oak.sonark

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
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

        val repository = MusicRepository(contentResolver)
        val settingsRepository = SettingsRepository(applicationContext)

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
                        } catch (e: ApiException) {
                            settingsViewModel.setGoogleAccount(null)
                        }
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

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val isGranted = permissions.values.all { it }
                    if (isGranted) {
                        viewModel.loadSongs()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                val requestPermission = {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                    if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.loadSongs()
                    } else {
                        permissionLauncher.launch(arrayOf(permission))
                    }
                }

                LaunchedEffect(Unit) {
                    requestPermission()
                    
                    // Check for existing Google account
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                    if (lastAccount != null) {
                        settingsViewModel.setGoogleAccount(lastAccount.email)
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
                                    songs = songs,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    sortOrder = sortOrder,
                                    onSortOrderChange = { viewModel.setSortOrder(it) },
                                    onSongClick = { song ->
                                        playbackViewModel.playQueue(songs, songs.indexOf(song))
                                        navigator.navigate(PlayerKey)
                                    },
                                    onRefresh = requestPermission
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
                                    onConnectGoogleDrive = {
                                        if (settingsViewModel.googleAccountName.value == null) {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        } else {
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                settingsViewModel.setGoogleAccount(null)
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
