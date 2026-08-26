package md.oak.sonark.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.*

@Composable
fun CircularWavyProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(500),
        label = "amplitude"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Album Art: Fill most of the area
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.75f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Wavy Progress
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.center
            val strokeWidth = 2.2.dp.toPx()
            val radius = (size.minDimension / 2) - (strokeWidth / 2)
            
            val maxAmplitude = 1.8.dp.toPx()
            val wavesCount = 6
            
            // Draw Background Track
            drawCircle(
                color = inactiveColor,
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw Active Wavy Track
            if (progress > 0f) {
                val path = Path()
                val sweepAngle = progress * 360f
                val startAngle = -90f // Top center
                
                val segments = (sweepAngle * 2).toInt().coerceAtLeast(1) // 2 segments per degree for smoothness
                
                for (i in 0..segments) {
                    val angleDeg = startAngle + (i.toFloat() / segments * sweepAngle)
                    val angleRad = angleDeg * PI.toFloat() / 180f
                    
                    // Wavy modulation
                    val waveAngle = (i.toFloat() / segments * sweepAngle) / 360f * 2 * PI.toFloat() * wavesCount
                    val modulation = maxAmplitude * amplitudeMultiplier * sin(waveAngle - phase)
                    
                    val x = center.x + (radius + modulation) * cos(angleRad)
                    val y = center.y + (radius + modulation) * sin(angleRad)
                    
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

private val androidx.compose.ui.geometry.Size.center: androidx.compose.ui.geometry.Offset
    get() = androidx.compose.ui.geometry.Offset(width / 2, height / 2)
