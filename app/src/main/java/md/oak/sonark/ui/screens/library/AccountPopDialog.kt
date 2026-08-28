package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import md.oak.sonark.data.repository.StorageQuota
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.SortOrder
import md.oak.sonark.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPopDialog(
    activeAccount: UserAccount?,
    otherAccounts: List<UserAccount>,
    storageQuota: StorageQuota?,
    isGuestMode: Boolean,
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onGuestModeClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onAccountClick: (UserAccount) -> Unit,
    onUrlClick: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AccountDialogContent(
            activeAccount = activeAccount,
            otherAccounts = otherAccounts,
            storageQuota = storageQuota,
            isGuestMode = isGuestMode,
            downloadQueueSize = downloadQueueSize,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            onRefresh = onRefresh,
            onQueueClick = onQueueClick,
            onSettingsClick = onSettingsClick,
            onAddAccountClick = onAddAccountClick,
            onManageAccountsClick = onManageAccountsClick,
            onGuestModeClick = onGuestModeClick,
            onSignOutClick = onSignOutClick,
            onAccountClick = onAccountClick,
            onUrlClick = onUrlClick,
            onCloseClick = onDismissRequest
        )
    }
}

@Composable
private fun AccountDialogContent(
    activeAccount: UserAccount?,
    otherAccounts: List<UserAccount>,
    storageQuota: StorageQuota?,
    isGuestMode: Boolean,
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onGuestModeClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onAccountClick: (UserAccount) -> Unit,
    onUrlClick: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
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
            // Main Account Area
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            user = activeAccount,
                            size = 48.dp,
                            isGuest = isGuestMode
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeAccount?.name ?: if (isGuestMode) "Guest User" else "Not Signed In",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeAccount?.email ?: "Sign in to sync your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeAccount?.isPro == true) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "Pro",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isExpanded) {
                        otherAccounts.forEach { account ->
                            AccountItem(account = account, onClick = { onAccountClick(account) })
                        }
                        
                        AccountActionItem(
                            icon = Icons.Default.DoNotDisturbOn,
                            title = "Use without an account",
                            onClick = onGuestModeClick
                        )
                        AccountActionItem(
                            icon = Icons.Default.PersonAdd,
                            title = "Add another account",
                            onClick = onAddAccountClick
                        )
                        if (activeAccount != null) {
                            AccountActionItem(
                                icon = Icons.Default.Close,
                                title = "Sign out of this account",
                                onClick = onSignOutClick
                            )
                        }
                        AccountActionItem(
                            icon = Icons.Default.ManageAccounts,
                            title = "Manage accounts on this device",
                            onClick = onManageAccountsClick
                        )
                    }

                    OutlinedButton(
                        onClick = { onUrlClick("https://myaccount.google.com/") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Manage your Google Account", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Storage Section
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (storageQuota != null) {
                                "${formatSize(storageQuota.usedBytes)} used of ${formatSize(storageQuota.totalBytes)}"
                            } else "Storage information unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (storageQuota != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { storageQuota.usedPercentage },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onUrlClick("https://one.google.com/storage") }) {
                            Text("Get storage", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onUrlClick("https://photos.google.com/quotamanagement") }) {
                            Text("Clean up storage", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Backup Section
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backup is off",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "To keep your music safe, back it up to your Google Account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { onUrlClick("https://photos.google.com/settings/backup") },
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                        ) {
                            Text("Turn on backup", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            AccountDialogItem(icon = Icons.Default.Refresh, title = "Refresh Library", onClick = { onRefresh(); onCloseClick() })

            var showSortOptions by remember { mutableStateOf(false) }
            AccountDialogItem(icon = Icons.Default.Settings, title = "Sort by: ${if (sortOrder == SortOrder.TITLE) "Title" else "Artist"}", onClick = { showSortOptions = !showSortOptions })
            if (showSortOptions) {
                Row(modifier = Modifier.padding(start = 48.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sortOrder == SortOrder.TITLE, onClick = { onSortOrderChange(SortOrder.TITLE) }, label = { Text("Title") })
                    FilterChip(selected = sortOrder == SortOrder.ARTIST, onClick = { onSortOrderChange(SortOrder.ARTIST) }, label = { Text("Artist") })
                }
            }

            AccountDialogItem(icon = Icons.Default.CloudDownload, title = "Download Queue", badge = if (downloadQueueSize > 0) "$downloadQueueSize" else null, onClick = onQueueClick)

            HorizontalDivider()

            AccountDialogItem(icon = Icons.Default.Settings, title = "Settings", onClick = onSettingsClick)
            AccountDialogItem(icon = Icons.AutoMirrored.Rounded.HelpOutline, title = "Help & Feedback", onClick = { onUrlClick("https://support.google.com/") })

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Privacy Policy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onUrlClick("https://policies.google.com/privacy") })
                Text(text = " • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Terms of Service", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onUrlClick("https://policies.google.com/terms") })
            }
        }
    }
}

@Composable
fun AccountItem(account: UserAccount, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user = account, size = 32.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AccountActionItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
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

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1.0 -> "%.2f TB".format(tb)
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        else -> "%.2f KB".format(kb)
    }
}
