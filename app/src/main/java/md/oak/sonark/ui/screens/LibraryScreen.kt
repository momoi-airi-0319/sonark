package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.Song
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.UIState
import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.theme.SonarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: UIState,
    songs: List<Song>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onSongClick: (Song) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Title") },
                            onClick = {
                                onSortOrderChange(SortOrder.TITLE)
                                showSortMenu = false
                            },
                            trailingIcon = { if (sortOrder == SortOrder.TITLE) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Artist") },
                            onClick = {
                                onSortOrderChange(SortOrder.ARTIST)
                                showSortMenu = false
                            },
                            trailingIcon = { if (sortOrder == SortOrder.ARTIST) Text("✓") }
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search songs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState) {
                    UIState.LOADING -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    UIState.PERMISSION_DENIED -> {
                        EmptyState(
                            icon = Icons.Rounded.Security,
                            message = "Permission Denied",
                            description = "Permission required to access music files. Please grant permission to scan your local storage.",
                            actionLabel = "Grant Permission",
                            onAction = onRefresh,
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
                            description = "No songs found. Please check your storage or enable Local Storage in Settings.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    UIState.SUCCESS -> {
                        if (songs.isEmpty() && searchQuery.isNotEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Search,
                                message = "No Results",
                                description = "No songs found matching \"$searchQuery\"",
                                actionLabel = "Clear Search",
                                onAction = { onSearchQueryChange("") },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (songs.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.LibraryMusic,
                                message = "No Songs Found",
                                description = "No songs found. Please check your storage or enable Local Storage in Settings.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            if (isTablet) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 180.dp),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(songs) { song ->
                                        LibraryGridItem(song = song, onClick = { onSongClick(song) })
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(songs) { song ->
                                        ListItem(
                                            headlineContent = { Text(song.title) },
                                            supportingContent = { Text(song.artist) },
                                            leadingContent = {
                                                Surface(
                                                    modifier = Modifier.size(48.dp),
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = song.title.firstOrNull()?.uppercase() ?: "?",
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.clickable { onSongClick(song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LibraryGridItem(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = song.title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenLoadingPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.LOADING,
            songs = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onSongClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenEmptyPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.EMPTY,
            songs = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onSongClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenPermissionPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.PERMISSION_DENIED,
            songs = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onSongClick = {},
            onRefresh = {}
        )
    }
}
