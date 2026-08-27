package md.oak.sonark.ui.screens.library

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import md.oak.sonark.ui.SortOrder
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.tooling.preview.Preview
import md.oak.sonark.ui.theme.SonarkTheme

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

@Preview(showBackground = true)
@Composable
fun AccountDialogContentPreview() {
    SonarkTheme {
        Surface {
            AccountDialogContent(
                googleAccountName = "airi@example.com",
                downloadQueueSize = 5,
                sortOrder = SortOrder.TITLE,
                onSortOrderChange = {},
                onRefresh = {},
                onQueueClick = {},
                onSettingsClick = {},
                onCloseClick = {}
            )
        }
    }
}
