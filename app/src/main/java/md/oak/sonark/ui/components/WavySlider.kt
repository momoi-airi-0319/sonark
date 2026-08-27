package md.oak.sonark.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import md.oak.sonark.ui.theme.SonarkTheme
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
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val displayValue = draggingValue ?: value

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
                detectTapGestures(
                    onTap = { offset ->
                        val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        draggingValue?.let { onValueChange(it) }
                        draggingValue = null
                    },
                    onDragCancel = {
                        draggingValue = null
                    },
                    onDrag = { change, dragAmount ->
                        val currentDraggingValue = draggingValue ?: value
                        val newValue = (currentDraggingValue + dragAmount.x / size.width).coerceIn(0f, 1f)
                        draggingValue = newValue
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            
            val activeWidth = width * displayValue
            
            val maxAmplitude = 6.dp.toPx()
            val waveLength = 40.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            
            // Draw Active Wave
            if (activeWidth > 0) {
                val activePath = Path().apply {
                    val resolution = 2 // px
                    for (x in 0..activeWidth.toInt() step resolution) {
                        val xFloat = x.toFloat()
                        // Wave emanates from the thumb (activeWidth)
                        val relativeX = (activeWidth - xFloat) / waveLength * 2 * PI.toFloat()
                        // Use (relativeX - phase) to propagate the wave to the left
                        val y = centerY + maxAmplitude * amplitudeMultiplier * sin(relativeX - phase)
                        
                        if (x == 0) moveTo(xFloat, y) else lineTo(xFloat, y)
                    }
                    // Ensure the path reaches the thumb precisely
                    val finalY = centerY + maxAmplitude * amplitudeMultiplier * sin(0f - phase)
                    lineTo(activeWidth, finalY)
                }
                
                drawPath(
                    path = activePath,
                    color = activeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            // Draw Inactive Line
            if (activeWidth < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(activeWidth, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // Draw Thumb (Vertical Bar/Capsule)
            val thumbBarWidth = 4.dp.toPx()
            val thumbBarHeight = 24.dp.toPx()
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(activeWidth - thumbBarWidth / 2, centerY - thumbBarHeight / 2),
                size = Size(thumbBarWidth, thumbBarHeight),
                cornerRadius = CornerRadius(thumbBarWidth / 2, thumbBarWidth / 2)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WavySliderPreview() {
    SonarkTheme {
        var value by remember { mutableFloatStateOf(0.5f) }
        Column(modifier = Modifier.padding(16.dp)) {
            WavySlider(
                value = value,
                onValueChange = { value = it },
                isPlaying = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            WavySlider(
                value = value,
                onValueChange = { value = it },
                isPlaying = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
