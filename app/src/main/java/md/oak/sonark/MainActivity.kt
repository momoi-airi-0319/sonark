package md.oak.sonark

import android.content.Intent
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.NavDisplay
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.Dependencies
import md.oak.sonark.navigation.*
import md.oak.sonark.playback.PlaybackService
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.components.DownloadQueueBottomSheet
import md.oak.sonark.ui.components.FloatingNavItem
import md.oak.sonark.ui.screens.library.AccountPopDialog
import md.oak.sonark.ui.screens.library.FloatingTopBar
import md.oak.sonark.ui.theme.SonarkTheme

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private val intentState = mutableStateOf<Intent?>(null)

    @androidx.media3.common.util.UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Dependencies.init(applicationContext)
        authManager = AuthManager(this)
        intentState.value = intent
        
        setupCoil()

        setContent {
            SonarkTheme {
                val viewModel: MainViewModel = viewModel { 
                    MainViewModel(Dependencies.musicRepository, Dependencies.accountRepository) 
                }
                val searchViewModel: SearchViewModel = viewModel { SearchViewModel(Dependencies.musicRepository) }
                val playbackViewModel: PlaybackViewModel = viewModel { PlaybackViewModel(application) }
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(Dependencies.settingsRepository) }
                
                val googleSignInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        authManager.handleSignInResult(
                            data = result.data,
                            onSuccess = { account ->
                                settingsViewModel.setGoogleAccount(account.email)
                                viewModel.loadSongs()
                            },
                        ) { e ->
                            Log.e("Sonark", "Sign-in failed with status code: ${e.statusCode}")
                            Toast.makeText(this@MainActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                            settingsViewModel.setGoogleAccount(null)
                            viewModel.setUnauthenticated()
                        }
                    }
                }

                val downloadQueueState by viewModel.downloadQueue.collectAsStateWithLifecycle()
                val googleAccountName by settingsViewModel.googleAccountName.collectAsStateWithLifecycle()
                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                val storageQuota by viewModel.storageQuota.collectAsStateWithLifecycle()
                val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
                val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

                val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
                val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
                val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
                val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

                val activeAccount = remember(accounts, googleAccountName) {
                    if (googleAccountName == null) null
                    else accounts.find { it.email.equals(googleAccountName, ignoreCase = true) }
                        ?: md.oak.sonark.data.repository.UserAccount(
                            name = googleAccountName?.split("@")?.firstOrNull() ?: "User",
                            email = googleAccountName!!,
                        )
                }
                val otherAccounts = remember(accounts, activeAccount) {
                    accounts.filter { it.email != activeAccount?.email }
                }
                val queueSize = remember(downloadQueueState) {
                    downloadQueueState.sumOf { it.totalSongs }
                }

                val navigationState = rememberNavigationState(
                    startRoute = HomeKey,
                    topLevelRoutes = setOf(HomeKey, LibraryKey, SettingsKey),
                )
                val navigator = remember { Navigator(navigationState) }
                
                var showQueue by remember { mutableStateOf(value = false) }
                var showAccountDialog by remember { mutableStateOf(value = false) }

                LaunchedEffect(intentState.value) {
                    val currentIntent = intentState.value
                    Log.d("MainActivity", "handleIntent: action=${currentIntent?.action}")
                    if (currentIntent?.action == PlaybackService.ACTION_SHOW_PLAYER) {
                        navigator.navigate(PlayerKey)
                        currentIntent.action = null // Clear action
                    }
                }

                LaunchedEffect(Unit) {
                    val lastAccount = authManager.getLastSignedInAccount()
                    if (lastAccount != null) {
                        settingsViewModel.setGoogleAccount(lastAccount.email)
                        authManager.updateDriveService(lastAccount)
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

                val myEntryProvider = createNavEntryProvider(
                    viewModel = viewModel,
                    searchViewModel = searchViewModel,
                    playbackViewModel = playbackViewModel,
                    navigator = navigator
                )

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        NavDisplay(
                            entries = navigationState.toEntries(myEntryProvider),
                        ) {
                            navigator.goBack()
                        }

                        val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute]
                        val isAtTopLevelRoot = (currentBackStack?.size ?: 0) <= 1
                        val showBars = (isAtTopLevelRoot && (navigationState.topLevelRoute != SettingsKey))

                        if (showBars) {
                            FloatingTopBar(
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                progress = if (duration > 0) playbackProgress.toFloat() / duration.toFloat() else 0f,
                                activeAccount = activeAccount,
                                isGuestMode = isGuestMode,
                                onPlayerClick = { navigator.navigate(PlayerKey) },
                                onAccountClick = { showAccountDialog = true }
                            )

                            Box(
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                SonarkBottomBar(navigationState, navigator)
                            }
                        }
                    }

                    if (showQueue) {
                        DownloadQueueBottomSheet(
                            queue = downloadQueueState,
                            onDismissRequest = { showQueue = false }
                        )
                    }

                    if (showAccountDialog) {
                        AccountPopDialog(
                            activeAccount = activeAccount,
                            otherAccounts = otherAccounts,
                            storageQuota = storageQuota,
                            isGuestMode = isGuestMode,
                            downloadQueueSize = queueSize,
                            sortOrder = sortOrder,
                            onSortOrderChange = { viewModel.setSortOrder(it) },
                            onRefresh = { viewModel.loadSongs() },
                            onQueueClick = {
                                showAccountDialog = false
                                showQueue = true
                            },
                            onSettingsClick = {
                                showAccountDialog = false
                                navigator.navigate(SettingsKey)
                            },
                            onAddAccountClick = {
                                showAccountDialog = false
                                googleSignInLauncher.launch(authManager.googleSignInClient.signInIntent)
                            },
                            onManageAccountsClick = {
                                showAccountDialog = false
                                Toast.makeText(this@MainActivity, "Manage Accounts not implemented", Toast.LENGTH_SHORT).show()
                            },
                            onGuestModeClick = {
                                showAccountDialog = false
                                viewModel.setGuestMode(enabled = true)
                            },
                            onSignOutClick = {
                                showAccountDialog = false
                                authManager.signOut {
                                    settingsViewModel.setGoogleAccount(null)
                                    viewModel.loadSongs()
                                }
                            },
                            onAccountClick = { account ->
                                showAccountDialog = false
                                settingsViewModel.setGoogleAccount(account.email)
                                viewModel.loadSongs()
                            },
                            onUrlClick = { url ->
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                this@MainActivity.startActivity(intent)
                            },
                            onDismissRequest = { showAccountDialog = false }
                        )
                    }
                }
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }

    private fun setupCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(
                    Interceptor { chain ->
                        val request = chain.request
                        val url = request.data.toString()
                        if (url.contains("googleapis.com") || url.contains("drive.google.com")) {
                            val token = runCatching {
                                Dependencies.driveProvider.credential?.token
                            }.getOrNull()

                            if (token != null) {
                                val authenticatedRequest = request.newBuilder()
                                    .addHeader("Authorization", "Bearer $token")
                                    .build()
                                return@Interceptor chain.proceed(authenticatedRequest)
                            }
                        }
                        chain.proceed(request)
                    },
                )
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    @Composable
    private fun SonarkBottomBar(
        navigationState: NavigationState,
        navigator: Navigator
    ) {
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
                        selected = navigationState.topLevelRoute == HomeKey,
                        onClick = { navigator.navigate(HomeKey) },
                        icon = Icons.Default.Home,
                        label = "Home"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
