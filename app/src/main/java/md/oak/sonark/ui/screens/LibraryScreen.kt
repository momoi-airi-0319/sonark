package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import md.oak.sonark.data.model.Album
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.UIState
import md.oak.sonark.ui.model.AlbumUiItem
import md.oak.sonark.ui.theme.SonarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: UIState,
    albums: List<Album>,
    downloadQueueSize: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    Box(contentAlignment = Alignment.Center) {
                        if (downloadQueueSize > 0) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onQueueClick) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Queue",
                                tint = if (downloadQueueSize > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
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
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(albums) { album ->
                                    val uiItem = remember(album) { AlbumUiItem.from(album) }
                                    uiItem.GridItem(onClick = { onAlbumClick(album) })
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

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun LibraryScreenLoadingPreview() {
    SonarkTheme {
        LibraryScreen(
            uiState = UIState.LOADING,
            albums = emptyList(),
            downloadQueueSize = 0,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onRefresh = {},
            onQueueClick = {}
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
            downloadQueueSize = 0,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onRefresh = {},
            onQueueClick = {}
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
            downloadQueueSize = 0,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOrder = SortOrder.TITLE,
            onSortOrderChange = {},
            onAlbumClick = {},
            onRefresh = {},
            onQueueClick = {}
        )
    }
}
