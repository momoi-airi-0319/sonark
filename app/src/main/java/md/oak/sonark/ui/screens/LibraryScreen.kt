package md.oak.sonark.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.components.CircularWavyProgressIndicator
import md.oak.sonark.ui.model.AlbumUiItem
import md.oak.sonark.ui.theme.SonarkTheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: UIState,
    albums: List<Album>,
    googleAccountName: String?,
    downloadQueueSize: Int,
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayerClick: () -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LibraryGrid(
                uiState = uiState,
                albums = albums,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onAlbumClick = onAlbumClick,
                onRefresh = onRefresh
            )

            FloatingTopBar(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = progress,
                googleAccountName = googleAccountName,
                onPlayerClick = onPlayerClick,
                onAccountClick = { showAccountDialog = true }
            )
        }
    }

    if (showAccountDialog) {
        AccountPopDialog(
            googleAccountName = googleAccountName,
            downloadQueueSize = downloadQueueSize,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            onRefresh = onRefresh,
            onQueueClick = {
                showAccountDialog = false
                onQueueClick()
            },
            onSettingsClick = {
                showAccountDialog = false
                onSettingsClick()
            },
            onDismissRequest = { showAccountDialog = false }
        )
    }
}

@Composable
private fun LibraryGrid(
    uiState: UIState,
    albums: List<Album>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            UIState.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            UIState.UNAUTHENTICATED -> {
                EmptyState(
                    icon = Icons.Rounded.Security,
                    message = "Not Connected",
                    description = "Please connect your Google Drive account in Settings to access your music.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.ERROR -> {
                EmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    message = "Error Loading Library",
                    description = "An error occurred while loading songs. Please try again.",
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.EMPTY -> {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    message = "No Songs Found",
                    description = "No songs found in your 'Vault' directory on Google Drive.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            UIState.SUCCESS -> {
                if (albums.isEmpty() && searchQuery.isNotEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        message = "No Results",
                        description = "No albums found matching \"$searchQuery\"",
                        actionLabel = "Clear Search",
                        onAction = { onSearchQueryChange("") },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (albums.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        message = "No Songs Found",
                        description = "No songs found in your 'Vault' directory on Google Drive.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 160.dp, end = 16.dp, bottom = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.title }) { album ->
                            val uiItem = remember(album) { AlbumUiItem.from(album) }
                            uiItem.GridItem(onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingTopBar(
    currentSong: SyncSong?,
    isPlaying: Boolean,
    progress: Float,
    googleAccountName: String?,
    onPlayerClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Pill
        Surface(
            onClick = onAccountClick,
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (googleAccountName != null) {
                            Text(
                                text = googleAccountName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Playback Status Pill
        Surface(
            onClick = onPlayerClick,
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .height(56.dp)
                .weight(1f, fill = false)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (currentSong != null) 6.dp else 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            ) {
                if (currentSong != null) {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        isPlaying = isPlaying,
                        imageUrl = currentSong.song.imageUrl,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentSong?.song?.title ?: "Sonark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AccountPopDialog(
    googleAccountName: String?,
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { 1000.dp.toPx() } // Fallback baseline
    val initialOffset = with(density) { 80.dp.toPx() }

    val offsetY = remember { Animatable(initialOffset) }
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.2f else 0f,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "ScrimAlpha"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(50.milliseconds)
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { isVisible = false },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha * (1f - (offsetY.value - initialOffset) / screenHeightPx).coerceIn(0f, 1f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { isVisible = false }
                )
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(
                    initialScale = 0.8f,
                    transformOrigin = TransformOrigin(0.9f, 0f),
                    animationSpec = tween(50, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(50, easing = FastOutSlowInEasing)),
                exit = scaleOut(
                    targetScale = 0.8f,
                    transformOrigin = TransformOrigin(0.9f, 0f),
                    animationSpec = tween(50, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(50, easing = FastOutSlowInEasing)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .offset { IntOffset(0, offsetY.value.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f))
                                }
                            },
                            onDragStopped = { velocity ->
                                if (offsetY.value > initialOffset + 100.dp.value || velocity > 500f) {
                                    scope.launch {
                                        offsetY.animateTo(screenHeightPx, tween(200, easing = FastOutSlowInEasing))
                                        isVisible = false
                                    }
                                } else {
                                    scope.launch {
                                        offsetY.animateTo(initialOffset, tween(200, easing = FastOutSlowInEasing))
                                    }
                                }
                            }
                        )
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { }
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        AccountDialogContent(
                            googleAccountName = googleAccountName,
                            downloadQueueSize = downloadQueueSize,
                            sortOrder = sortOrder,
                            onSortOrderChange = onSortOrderChange,
                            onRefresh = onRefresh,
                            onQueueClick = onQueueClick,
                            onSettingsClick = onSettingsClick,
                            onCloseClick = { isVisible = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDialogContent(
    googleAccountName: String?,
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onCloseClick, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Text(
                text = "Sonark",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (googleAccountName != null) {
                                    Text(
                                        text = googleAccountName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = googleAccountName?.substringBefore("@") ?: "Not Connected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(text = googleAccountName ?: "Sign in to sync your music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("管理您的 Sonark 账号", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            AccountDialogItem(icon = Icons.Default.Refresh, title = "刷新媒体库", onClick = { onRefresh(); onCloseClick() })

            var showSortOptions by remember { mutableStateOf(false) }
            AccountDialogItem(icon = Icons.Default.Settings, title = "排序方式：${if (sortOrder == SortOrder.TITLE) "标题" else "艺术家"}", onClick = { showSortOptions = !showSortOptions })
            if (showSortOptions) {
                Row(modifier = Modifier.padding(start = 48.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortOrder == SortOrder.TITLE, onClick = { onSortOrderChange(SortOrder.TITLE) }, label = { Text("标题") })
                    FilterChip(selected = sortOrder == SortOrder.ARTIST, onClick = { onSortOrderChange(SortOrder.ARTIST) }, label = { Text("艺术家") })
                }
            }

            AccountDialogItem(icon = Icons.Default.CloudDownload, title = "下载队列", badge = if (downloadQueueSize > 0) "$downloadQueueSize" else null, onClick = onQueueClick)

            HorizontalDivider()

            AccountDialogItem(icon = Icons.Default.Settings, title = "设置", onClick = onSettingsClick)
            AccountDialogItem(icon = Icons.AutoMirrored.Rounded.HelpOutline, title = "帮助与反馈", onClick = { })

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "隐私权政策", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = " • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "服务条款", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AccountDialogItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge != null) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = badge, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenLoadingPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.LOADING,
            albums = emptyList(),
            googleAccountName = null,
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenEmptyPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.EMPTY,
            albums = emptyList(),
            googleAccountName = "user@gmail.com",
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenUnauthenticatedPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.UNAUTHENTICATED,
            albums = emptyList(),
            googleAccountName = null,
            downloadQueueSize = 0,
            currentSong = null,
            isPlaying = false,
            progress = 0f,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onPlayerClick = {},
            onRefresh = {},
            onQueueClick = {},
            onSettingsClick = {}
        )
    }
}
