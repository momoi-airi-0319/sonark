package md.oak.sonark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
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
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import md.oak.sonark.auth.AuthManager
import md.oak.sonark.data.Dependencies
import md.oak.sonark.navigation.*
import md.oak.sonark.playback.PlaybackService
import md.oak.sonark.ui.MainViewModel
import md.oak.sonark.ui.PlaybackViewModel
import md.oak.sonark.ui.SearchViewModel
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.DownloadQueueBottomSheet
import md.oak.sonark.ui.components.FloatingNavItem
import md.oak.sonark.ui.screens.library.AccountPopDialog
import md.oak.sonark.ui.screens.library.FloatingTopBar
import md.oak.sonark.ui.theme.SonarkTheme

@Composable
fun LoginScreen(onSignInClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = "Sonark Logo",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Sonark",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onSignInClick,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Sign in with Google")
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val authManager: AuthManager get() = Dependencies.authManager
    private val intentState = mutableStateOf<Intent?>(null)

    @androidx.media3.common.util.UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("MainActivity", "ON_CREATE_TRIGGERED")
        enableEdgeToEdge()

        Dependencies.init(applicationContext)
        intentState.value = intent
        
        setupCoil()

        setContent {
            SonarkTheme {
                val viewModel: MainViewModel = viewModel { 
                    MainViewModel(Dependencies.musicRepository, Dependencies.accountRepository, Dependencies.settingsRepository) 
                }
                val searchViewModel: SearchViewModel = viewModel { SearchViewModel(Dependencies.musicRepository) }
                val playbackViewModel: PlaybackViewModel = viewModel { PlaybackViewModel(application) }
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(Dependencies.settingsRepository) }
                
                val coroutineScope = rememberCoroutineScope()
                val downloadQueueState by viewModel.downloadQueue.collectAsStateWithLifecycle()
                val googleAccountName by settingsViewModel.googleAccountName.collectAsStateWithLifecycle()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val accounts by viewModel.accounts.collectAsStateWithLifecycle()
                val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
                val otherAccounts by viewModel.otherAccounts.collectAsStateWithLifecycle()
                val storageQuota by viewModel.storageQuota.collectAsStateWithLifecycle()
                val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

                val isPlaying by playbackViewModel.isPlaying.collectAsStateWithLifecycle()
                val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
                val playbackProgress by playbackViewModel.playbackProgress.collectAsStateWithLifecycle()
                val duration by playbackViewModel.duration.collectAsStateWithLifecycle()

                val authLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    try {
                        val authResult = authManager.getAuthorizationClient()
                            .getAuthorizationResultFromIntent(result.data)
                        
                        settingsViewModel.setGoogleAccount(activeAccount?.email)
                        authManager.updateDriveService(authResult.accessToken, activeAccount?.email)
                        viewModel.loadSongs()
                    } catch (e: ApiException) {
                        Log.e("Sonark", "Authorization failed", e)
                        Toast.makeText(this@MainActivity, "Authorization failed", Toast.LENGTH_SHORT).show()
                    }
                }

                val performAuth = { email: String ->
                    val authRequest = authManager.createAuthorizationRequest(email)
                    authManager.getAuthorizationClient().authorize(authRequest)
                        .addOnSuccessListener { result ->
                            if (result.hasResolution()) {
                                val pendingIntent = result.pendingIntent
                                authLauncher.launch(IntentSenderRequest.Builder(pendingIntent!!.intentSender).build())
                            } else {
                                authManager.updateDriveService(result.accessToken, email)
                                viewModel.loadSongs()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Sonark", "Auth request failed", e)
                        }
                }

                val handleSignInResult = { credential: GoogleIdTokenCredential ->
                    val email = credential.id
                    settingsViewModel.setGoogleAccount(email)
                    settingsViewModel.addOrUpdateAccount(
                        md.oak.sonark.data.repository.UserAccount(
                            name = credential.displayName ?: email.split("@").first(),
                            email = email,
                            profileImageUrl = credential.profilePictureUri?.toString(),
                            isLoggedIn = true
                        )
                    )
                    performAuth(email)
                }

                val startSignIn = {
                    coroutineScope.launch {
                        when (val result = authManager.signIn()) {
                            is md.oak.sonark.auth.SignInResult.Success -> {
                                handleSignInResult(result.credential)
                            }
                            is md.oak.sonark.auth.SignInResult.Failure -> {
                                viewModel.setUnauthenticated()
                                val errorMsg = "Sign-in failed: ${result.type}\n${result.message ?: ""}"
                                Log.e("Sonark", errorMsg)
                                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
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
                    if (googleAccountName != null) {
                        performAuth(googleAccountName!!)
                    } else {
                        viewModel.setUnauthenticated()
                    }
                }

                LaunchedEffect(googleAccountName) {
                    playbackViewModel.stopPlayback()
                    if (googleAccountName != null) {
                        viewModel.refreshAccountInfo()
                    }
                }

                val myEntryProvider = createNavEntryProvider(
                    viewModel = viewModel,
                    searchViewModel = searchViewModel,
                    playbackViewModel = playbackViewModel,
                    settingsViewModel = settingsViewModel,
                    navigator = navigator
                )

                val showLoginScreen = accounts.isEmpty() || accounts.all { !it.isLoggedIn }

                LaunchedEffect(accounts, googleAccountName) {
                    if (googleAccountName == null && accounts.any { it.isLoggedIn }) {
                        val firstLoggedIn = accounts.first { it.isLoggedIn }
                        settingsViewModel.setGoogleAccount(firstLoggedIn.email)
                    }
                }

                if (showLoginScreen) {
                    LoginScreen(
                        onSignInClick = {
                            startSignIn()
                        }
                    )
                } else {
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
                                onPauseAll = { viewModel.pauseAllDownloads() },
                                onResumeAll = { viewModel.resumeAllDownloads() },
                                onPauseSong = { viewModel.pauseDownload(it) },
                                onResumeSong = { viewModel.resumeDownload(it) },
                                onDismissRequest = { showQueue = false }
                            )
                        }

                        if (showAccountDialog) {
                            AccountPopDialog(
                                activeAccount = activeAccount,
                                otherAccounts = otherAccounts,
                                storageQuota = storageQuota,
                                downloadQueueSize = queueSize,
                                sortOrder = sortOrder,
                                isRefreshing = uiState == UIState.LOADING,
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
                                    startSignIn()
                                },
                                onSignOutAllClick = {
                                    showAccountDialog = false
                                    coroutineScope.launch {
                                        authManager.signOut {
                                            settingsViewModel.signOutAll()
                                            viewModel.loadSongs()
                                        }
                                    }
                                },
                                onAccountClick = { account ->
                                    showAccountDialog = false
                                    if (account.email != activeAccount?.email || account.hasConnectionError) {
                                        if (!account.isLoggedIn) {
                                            startSignIn()
                                        } else {
                                            settingsViewModel.setGoogleAccount(account.email)
                                            performAuth(account.email)
                                        }
                                    }
                                },
                                onLogTokenClick = { account ->
                                    if (account.email == activeAccount?.email) {
                                        coroutineScope.launch {
                                            val token = authManager.getLastKnownToken()
                                            Log.d("SonarkTest", "Token for ${account.email}: $token")
                                            Toast.makeText(this@MainActivity, "Token logged to Logcat", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(this@MainActivity, "Please switch to this account first to get its token", Toast.LENGTH_LONG).show()
                                    }
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
                            val token = authManager.getLastKnownToken()

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
