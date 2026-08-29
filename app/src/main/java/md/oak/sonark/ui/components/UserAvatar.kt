package md.oak.sonark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import md.oak.sonark.data.repository.UserAccount

@Composable
fun UserAvatar(
    user: UserAccount?,
    size: Dp,
    isSelected: Boolean = false,
    showEditIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.border(2.dp, Color(0xFF4285F4), CircleShape).padding(3.dp)
                    else Modifier
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (user?.profileImageUrl != null) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (user != null) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.7f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        if (showEditIcon) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
