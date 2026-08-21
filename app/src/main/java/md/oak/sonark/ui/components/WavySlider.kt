package md.oak.sonark.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
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

    Box(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            
            val activeWidth = width * value
            
            val maxAmplitude = 6.dp.toPx()
            val waveLength = 40.dp.toPx()
            
            // Draw Active Wave
            if (activeWidth > 0) {
                val activePath = Path().apply {
                    moveTo(0f, centerY)
                    val resolution = 2 // px
                    for (x in resolution..activeWidth.toInt() step resolution) {
                        val xFloat = x.toFloat()
                        val relativeX = xFloat / waveLength * 2 * PI.toFloat()
                        
                        // Dampen amplitude at both ends of the active segment
                        val dampening = when {
                            xFloat < 24.dp.toPx() -> xFloat / 24.dp.toPx()
                            activeWidth - xFloat < 24.dp.toPx() -> (activeWidth - xFloat) / 24.dp.toPx()
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        
                        val y = centerY + maxAmplitude * amplitudeMultiplier * dampening * sin(relativeX - phase)
                        lineTo(xFloat, y)
                    }
                    // Ensure it ends exactly at the thumb
                    lineTo(activeWidth, centerY)
                }
                
                drawPath(
                    path = activePath,
                    color = activeColor,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            // Draw Inactive Line
            if (activeWidth < width) {
                val inactivePath = Path().apply {
                    moveTo(activeWidth, centerY)
                    lineTo(width, centerY)
                }
                
                drawPath(
                    path = inactivePath,
                    color = inactiveColor,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            // Draw Thumb
            val thumbX = activeWidth
            val thumbY = centerY
            
            drawCircle(
                color = activeColor,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }
    }
}
