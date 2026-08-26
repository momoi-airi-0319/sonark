package md.oak.sonark.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onSongClick: (SyncSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.songs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search your music") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { /* Voice search TODO */ }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Search")
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (searchQuery.isEmpty()) {
                SearchSuggestions()
            } else {
                SearchResultsList(searchResults, onSongClick)
            }
        }
    }
}

@Composable
fun SearchSuggestions() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "尝试搜索您想听的内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "您可以说“轻快的音乐”或“我喜欢的周杰伦”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { CategoryItem(Icons.Default.VideoLibrary, "视频") }
        item { CategoryItem(Icons.Default.Screenshot, "屏幕截图") }
        item { CategoryItem(Icons.Default.StarBorder, "收藏") }
        item { CategoryItem(Icons.Default.AutoAwesome, "智能创作") }
        item { CategoryItem(Icons.Default.AccessTime, "最近添加") }
    }
}

@Composable
fun CategoryItem(icon: ImageVector, title: String) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        modifier = Modifier.clickable { /* TODO */ }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SearchResultsList(results: List<SyncSong>, onSongClick: (SyncSong) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { syncSong ->
            val song = syncSong.song
            ListItem(
                headlineContent = { Text(song.title) },
                supportingContent = { Text("${song.artist} • ${song.album}") },
                leadingContent = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                modifier = Modifier.clickable { onSongClick(syncSong) }
            )
        }
    }
}
