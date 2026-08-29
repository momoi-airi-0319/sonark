package md.oak.sonark.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    isRefreshing: Boolean = false,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onSignOutAllClick: () -> Unit,
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
            downloadQueueSize = downloadQueueSize,
            sortOrder = sortOrder,
            isRefreshing = isRefreshing,
            onSortOrderChange = onSortOrderChange,
            onRefresh = onRefresh,
            onQueueClick = onQueueClick,
            onSettingsClick = onSettingsClick,
            onAddAccountClick = onAddAccountClick,
            onSignOutAllClick = onSignOutAllClick,
            onAccountClick = onAccountClick,
            onUrlClick = onUrlClick,
            onCloseClick = onDismissRequest
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialogContent(
    activeAccount: UserAccount?,
    otherAccounts: List<UserAccount>,
    storageQuota: StorageQuota?,
    downloadQueueSize: Int,
    sortOrder: SortOrder,
    isRefreshing: Boolean,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onSignOutAllClick: () -> Unit,
    onAccountClick: (UserAccount) -> Unit,
    onUrlClick: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top header with "Sonark" and a close button
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
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // A card/surface containing: active account, other accounts, add account button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    // The active account at the top (larger avatar with blue ring and edit icon)
                    if (activeAccount != null) {
                        ActiveAccountItem(account = activeAccount)
                    }

                    // A list of other accounts
                    otherAccounts.forEach { account ->
                        AccountItem(account = account, onClick = { onAccountClick(account) })
                    }

                    // "Add another account" button
                    AccountActionItem(
                        icon = Icons.Default.PersonAddAlt1,
                        title = "Add another account",
                        onClick = onAddAccountClick
                    )
                }
            }

            // A separate card/surface for "Sign out of all accounts"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = onSignOutAllClick
            ) {
                AccountActionItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign out of all accounts",
                    onClick = onSignOutAllClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Below that, the library management items (Refresh, Sort, Queue, Settings)
            AccountDialogItem(
                icon = Icons.Default.Refresh,
                title = "Refresh Library",
                onClick = { onRefresh(); onCloseClick() }
            )

            SortItem(
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange
            )

            AccountDialogItem(
                icon = Icons.Default.CloudDownload,
                title = "Download Queue",
                badge = if (downloadQueueSize > 0) "$downloadQueueSize" else null,
                onClick = onQueueClick
            )

            AccountDialogItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                onClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy & Terms
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onUrlClick("https://policies.google.com/privacy") }
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onUrlClick("https://policies.google.com/terms") }
                )
            }
        }
    }
}

@Composable
private fun ActiveAccountItem(account: UserAccount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            user = account,
            size = 48.dp,
            isSelected = true,
            showEditIcon = false
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = account.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountItem(account: UserAccount, onClick: () -> Unit) {
    val alpha = if (account.isLoggedIn) 1f else 0.5f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.alpha(alpha)) {
            UserAvatar(user = account, size = 32.dp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).alpha(alpha)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = account.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (account.hasConnectionError) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Connection Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )
        }
        if (!account.isLoggedIn) {
            Text(
                text = "Signed out",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AccountActionItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AccountDialogItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortItem(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var showSortOptions by remember { mutableStateOf(false) }
    
    Column {
        AccountDialogItem(
            icon = Icons.Default.SortByAlpha,
            title = "Sort by: ${if (sortOrder == SortOrder.TITLE) "Title" else "Artist"}",
            onClick = { showSortOptions = !showSortOptions }
        )
        if (showSortOptions) {
            Row(
                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = sortOrder == SortOrder.TITLE,
                    onClick = { onSortOrderChange(SortOrder.TITLE) },
                    label = { Text("Title") }
                )
                FilterChip(
                    selected = sortOrder == SortOrder.ARTIST,
                    onClick = { onSortOrderChange(SortOrder.ARTIST) },
                    label = { Text("Artist") }
                )
            }
        }
    }
}
