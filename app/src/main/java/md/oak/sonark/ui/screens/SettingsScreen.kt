package md.oak.sonark.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.SettingsViewModel
import md.oak.sonark.ui.components.UserAvatar
import md.oak.sonark.ui.utils.Formatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.storedAccounts.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val maxConcurrentDownloads by viewModel.maxConcurrentDownloads.collectAsStateWithLifecycle()
    val pauseOnMeteredNetwork by viewModel.pauseOnMeteredNetwork.collectAsStateWithLifecycle()
    
    SettingsContent(
        accounts = accounts,
        storageUsage = storageUsage,
        maxConcurrentDownloads = maxConcurrentDownloads,
        pauseOnMeteredNetwork = pauseOnMeteredNetwork,
        onClearCache = { viewModel.clearCache(it) },
        onSetMaxConcurrent = { viewModel.setMaxConcurrentDownloads(it) },
        onSetPauseOnMetered = { viewModel.setPauseOnMeteredNetwork(it) },
        onBackClick = onBackClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    accounts: List<UserAccount>,
    storageUsage: Map<String, Long>,
    maxConcurrentDownloads: Int,
    pauseOnMeteredNetwork: Boolean,
    onClearCache: (String) -> Unit,
    onSetMaxConcurrent: (Int) -> Unit,
    onSetPauseOnMetered: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Download Settings") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "最大并发下载数", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "限制同时下载的任务数量 (当前: $maxConcurrentDownloads)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(
                            onClick = { if (maxConcurrentDownloads > 1) onSetMaxConcurrent(maxConcurrentDownloads - 1) },
                            enabled = maxConcurrentDownloads > 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "$maxConcurrentDownloads",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        OutlinedIconButton(
                            onClick = { if (maxConcurrentDownloads < 16) onSetMaxConcurrent(maxConcurrentDownloads + 1) },
                            enabled = maxConcurrentDownloads < 16,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "流量计费网络自动暂停", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "使用移动数据/计费网络时自动暂停下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pauseOnMeteredNetwork,
                        onCheckedChange = onSetPauseOnMetered
                    )
                }
            }

            SettingsSection(title = "Storage Management") {
                if (accounts.isEmpty()) {
                    Text(
                        text = "No accounts stored.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    accounts.forEach { account ->
                        AccountStorageItem(
                            account = account,
                            usage = storageUsage[account.email] ?: 0L,
                            onClearCache = { onClearCache(account.email) }
                        )
                    }
                }
            }

            SettingsSection(title = "About") {
                SettingsInfoItem(
                    title = "App Version",
                    subtitle = "1.1.0 (Rust Core)",
                    icon = Icons.Rounded.Info
                )
                SettingsInfoItem(
                    title = "About Sonark",
                    subtitle = "A modern music player powered by a high-performance Rust SDK.",
                    icon = Icons.AutoMirrored.Rounded.HelpOutline
                )
            }
        }
    }
}

@Composable
fun AccountStorageItem(
    account: UserAccount,
    usage: Long,
    onClearCache: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user = account, size = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${account.email} • ${Formatter.formatFileSize(usage)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onClearCache) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Clear Cache",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun SettingsInfoItem(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
