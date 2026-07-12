package com.networkscanner.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated radar: concentric rings with a rotating sweep while scanning and a
 * gentle expanding ping while idle. Each discovered device appears as a blip
 * placed on a golden-angle spiral, springing in as [blipCount] grows.
 */
@Composable
fun RadarHero(
    isScanning: Boolean,
    blipCount: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outlineVariant

    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing)),
        label = "pulse"
    )

    // Spring-in progress per blip index; cleared when a new scan resets the count.
    val blipProgress = remember { mutableStateMapOf<Int, Float>() }
    LaunchedEffect(blipCount) {
        if (blipCount == 0) blipProgress.clear()
        for (i in 0 until blipCount) {
            if (blipProgress.containsKey(i)) continue
            blipProgress[i] = 0f
            launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ -> blipProgress[i] = value }
            }
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = this.center
        val hairline = 1.dp.toPx()

        listOf(0.33f, 0.66f, 1f).forEach { frac ->
            drawCircle(ringColor, radius * frac, center, style = Stroke(hairline))
        }
        drawLine(ringColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), hairline)
        drawLine(ringColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), hairline)

        if (isScanning) {
            rotate(degrees = sweepAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to primary.copy(alpha = 0.35f),
                        center = center
                    ),
                    radius = radius,
                    center = center
                )
                drawLine(
                    color = primary.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = 2.dp.toPx()
                )
            }
        } else {
            val pingRadius = radius * (0.15f + 0.85f * pulse)
            drawCircle(
                color = primary.copy(alpha = 0.35f * (1f - pulse)),
                radius = pingRadius,
                center = center,
                style = Stroke(2.dp.toPx())
            )
        }

        drawCircle(primary, 4.dp.toPx(), center)

        blipProgress.forEach { (index, progress) ->
            if (progress <= 0f) return@forEach
            val angleRad = Math.toRadians((index * 137.508) % 360.0)
            val frac = 0.25f + 0.65f * ((index * 0.381966f) % 1f)
            val pos = Offset(
                center.x + (radius * frac * cos(angleRad)).toFloat(),
                center.y + (radius * frac * sin(angleRad)).toFloat()
            )
            drawCircle(primary.copy(alpha = 0.25f * progress), 9.dp.toPx() * progress, pos)
            drawCircle(primary.copy(alpha = progress), 4.dp.toPx() * progress, pos)
        }
    }
}
