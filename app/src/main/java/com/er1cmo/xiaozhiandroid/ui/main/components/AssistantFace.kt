package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.ui.theme.SoftAmber
import com.er1cmo.xiaozhiandroid.ui.theme.SoftClay
import com.er1cmo.xiaozhiandroid.ui.theme.SoftSand
import com.er1cmo.xiaozhiandroid.ui.theme.WarmSurface
import com.er1cmo.xiaozhiandroid.ui.theme.WarmText
import com.er1cmo.xiaozhiandroid.ui.theme.WarmTextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AssistantFace(
    state: ConversationState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = WarmSurface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(230.dp),
                contentAlignment = Alignment.Center,
            ) {
                OrganicAssistantBlob(
                    state = state,
                    modifier = Modifier.size(220.dp),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                color = WarmText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = WarmTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OrganicAssistantBlob(
    state: ConversationState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition()
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = PI2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = phaseDurationFor(state), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    val breath = transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Canvas(modifier = modifier) {
        val p = phase.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val verticalBob = when (state) {
            ConversationState.Speaking -> sin(p * 2.2f) * size.minDimension * 0.028f
            ConversationState.Listening -> sin(p * 3.4f) * size.minDimension * 0.008f
            ConversationState.Error -> size.minDimension * 0.018f
            else -> sin(p * 0.7f) * size.minDimension * 0.006f
        }
        val baseCenter = center.copy(y = center.y + verticalBob)
        val config = organicConfigFor(state, breath.value)
        val colors = colorsFor(state)

        if (state == ConversationState.Thinking) {
            drawBlobLayer(
                center = baseCenter.copy(x = baseCenter.x - size.minDimension * 0.18f + cos(p) * 12f),
                radius = size.minDimension * 0.28f,
                scaleX = 1.08f,
                scaleY = 0.96f,
                phase = p,
                amplitude = 0.16f,
                colors = colors,
                alpha = 0.72f,
            )
            drawBlobLayer(
                center = baseCenter.copy(x = baseCenter.x + size.minDimension * 0.18f - cos(p) * 12f),
                radius = size.minDimension * 0.28f,
                scaleX = 0.96f,
                scaleY = 1.08f,
                phase = p + 1.7f,
                amplitude = 0.16f,
                colors = colors,
                alpha = 0.62f,
            )
        } else {
            drawBlobLayer(
                center = baseCenter,
                radius = size.minDimension * config.radius,
                scaleX = config.scaleX,
                scaleY = config.scaleY,
                phase = p,
                amplitude = config.amplitude,
                colors = colors,
                alpha = config.alpha,
            )
            drawBlobLayer(
                center = baseCenter.copy(x = baseCenter.x + size.minDimension * 0.055f, y = baseCenter.y - size.minDimension * 0.04f),
                radius = size.minDimension * config.radius * 0.78f,
                scaleX = config.scaleX * 0.96f,
                scaleY = config.scaleY * 1.04f,
                phase = p + 2.1f,
                amplitude = config.amplitude * 0.74f,
                colors = colors.reversed(),
                alpha = config.alpha * 0.55f,
            )
            if (state == ConversationState.Listening) {
                repeat(3) { index ->
                    val ripple = size.minDimension * (0.35f + index * 0.085f + (sin(p * 2.6f + index) + 1f) * 0.012f)
                    drawCircle(
                        color = Color(0x33B98E5A),
                        radius = ripple,
                        center = baseCenter,
                        style = Stroke(width = (1.0f + index * 0.45f).dp.toPx()),
                    )
                }
            }
        }

        drawExpression(
            state = state,
            center = baseCenter,
            width = size.minDimension,
            phase = p,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlobLayer(
    center: Offset,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    phase: Float,
    amplitude: Float,
    colors: List<Color>,
    alpha: Float,
) {
    val path = organicBlobPath(
        centerX = center.x,
        centerY = center.y,
        radiusX = radius * scaleX,
        radiusY = radius * scaleY,
        phase = phase,
        amplitude = amplitude,
    )
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = colors.map { it.copy(alpha = it.alpha * alpha) },
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius),
        ),
    )
}

private fun organicBlobPath(
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    phase: Float,
    amplitude: Float,
): Path {
    val t = 0.55228475f
    val top = 1f + sin(phase) * amplitude
    val right = 1f + cos(phase * 0.82f + 0.7f) * amplitude * 0.86f
    val bottom = 1f + sin(phase * 1.18f + 1.8f) * amplitude * 0.92f
    val left = 1f + cos(phase * 1.05f + 2.4f) * amplitude * 0.78f
    val x1 = radiusX * right
    val y1 = radiusY * top
    val x2 = radiusX * left
    val y2 = radiusY * bottom

    return Path().apply {
        moveTo(centerX, centerY - y1)
        cubicTo(
            centerX + x1 * t, centerY - y1,
            centerX + x1, centerY - radiusY * right * t,
            centerX + x1, centerY,
        )
        cubicTo(
            centerX + x1, centerY + y2 * t,
            centerX + radiusX * bottom * t, centerY + y2,
            centerX, centerY + y2,
        )
        cubicTo(
            centerX - x2 * t, centerY + y2,
            centerX - x2, centerY + radiusY * left * t,
            centerX - x2, centerY,
        )
        cubicTo(
            centerX - x2, centerY - y1 * t,
            centerX - radiusX * top * t, centerY - y1,
            centerX, centerY - y1,
        )
        close()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpression(
    state: ConversationState,
    center: Offset,
    width: Float,
    phase: Float,
) {
    val faceColor = if (state == ConversationState.Error) Color(0xFF7D514C) else Color(0xFF4A392D)
    val eyeY = center.y - width * 0.045f
    val leftX = center.x - width * 0.08f
    val rightX = center.x + width * 0.08f
    val eyeRadius = width * 0.018f
    val stroke = Stroke(width = width * 0.012f, cap = StrokeCap.Round)

    when (state) {
        ConversationState.Idle -> {
            drawLine(faceColor, Offset(leftX - eyeRadius, eyeY), Offset(leftX + eyeRadius, eyeY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(faceColor, Offset(rightX - eyeRadius, eyeY), Offset(rightX + eyeRadius, eyeY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawOval(
                color = faceColor.copy(alpha = 0.72f),
                topLeft = Offset(center.x - width * 0.012f, center.y + width * 0.045f),
                size = Size(width * 0.024f, width * 0.014f),
            )
        }
        ConversationState.Connected -> {
            drawCircle(faceColor, eyeRadius, Offset(leftX, eyeY))
            drawCircle(faceColor, eyeRadius, Offset(rightX, eyeY))
            drawArc(
                color = faceColor,
                startAngle = 18f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = Offset(center.x - width * 0.055f, center.y + width * 0.005f),
                size = Size(width * 0.11f, width * 0.07f),
                style = stroke,
            )
        }
        ConversationState.Listening -> {
            val pulse = 1f + sin(phase * 3.5f) * 0.12f
            drawCircle(faceColor, eyeRadius * pulse, Offset(leftX, eyeY))
            drawCircle(faceColor, eyeRadius * pulse, Offset(rightX, eyeY))
            drawArc(
                color = faceColor,
                startAngle = 24f,
                sweepAngle = 132f,
                useCenter = false,
                topLeft = Offset(center.x - width * 0.05f, center.y + width * 0.012f),
                size = Size(width * 0.10f, width * 0.06f),
                style = stroke,
            )
        }
        ConversationState.Thinking, ConversationState.Activating, ConversationState.Connecting -> {
            val shift = sin(phase * 1.4f) * width * 0.007f
            drawCircle(faceColor, eyeRadius * 0.82f, Offset(leftX + shift, eyeY))
            drawCircle(faceColor, eyeRadius * 0.82f, Offset(rightX - shift, eyeY))
            drawLine(faceColor, Offset(center.x - width * 0.026f, center.y + width * 0.052f), Offset(center.x + width * 0.026f, center.y + width * 0.052f), strokeWidth = stroke.width * 0.8f, cap = StrokeCap.Round)
        }
        ConversationState.Speaking -> {
            val open = (0.55f + (sin(phase * 5.0f) + 1f) * 0.24f).coerceIn(0.35f, 1.0f)
            drawCircle(faceColor, eyeRadius, Offset(leftX, eyeY))
            drawCircle(faceColor, eyeRadius, Offset(rightX, eyeY))
            drawOval(
                color = faceColor,
                topLeft = Offset(center.x - width * 0.028f, center.y + width * 0.024f),
                size = Size(width * 0.056f, width * 0.046f * open),
            )
        }
        ConversationState.Error -> {
            drawLine(faceColor, Offset(leftX - eyeRadius, eyeY - eyeRadius), Offset(leftX + eyeRadius, eyeY + eyeRadius), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(faceColor, Offset(rightX - eyeRadius, eyeY + eyeRadius), Offset(rightX + eyeRadius, eyeY - eyeRadius), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawArc(
                color = faceColor,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - width * 0.045f, center.y + width * 0.045f),
                size = Size(width * 0.09f, width * 0.06f),
                style = stroke,
            )
        }
    }
}

private data class OrganicBlobConfig(
    val radius: Float,
    val scaleX: Float,
    val scaleY: Float,
    val amplitude: Float,
    val alpha: Float,
)

private fun organicConfigFor(state: ConversationState, breath: Float): OrganicBlobConfig {
    return when (state) {
        ConversationState.Idle -> OrganicBlobConfig(0.28f * breath, 0.86f, 1.06f, 0.045f, 0.86f)
        ConversationState.Connected -> OrganicBlobConfig(0.34f * breath, 1.02f, 0.98f, 0.080f, 0.90f)
        ConversationState.Listening -> OrganicBlobConfig(0.37f * breath, 1.05f, 0.98f, 0.175f, 0.92f)
        ConversationState.Thinking -> OrganicBlobConfig(0.34f * breath, 1.0f, 1.0f, 0.16f, 0.82f)
        ConversationState.Speaking -> OrganicBlobConfig(0.39f * breath, 1.04f, 0.98f, 0.245f, 0.94f)
        ConversationState.Error -> OrganicBlobConfig(0.30f * breath, 1.08f, 0.78f, 0.035f, 0.74f)
        ConversationState.Activating, ConversationState.Connecting -> OrganicBlobConfig(0.33f * breath, 0.96f, 1.04f, 0.115f, 0.86f)
    }
}

private fun colorsFor(state: ConversationState): List<Color> {
    return when (state) {
        ConversationState.Error -> listOf(SoftClay.copy(alpha = 0.92f), Color(0xFFE8D8CF).copy(alpha = 0.72f), Color(0xFFDCC1B9).copy(alpha = 0.58f))
        ConversationState.Listening -> listOf(SoftAmber.copy(alpha = 0.90f), Color(0xFFF6E5C0).copy(alpha = 0.70f), SoftSand.copy(alpha = 0.60f))
        ConversationState.Speaking -> listOf(Color(0xFFFFE8B8).copy(alpha = 0.92f), Color(0xFFF2D9A5).copy(alpha = 0.72f), SoftSand.copy(alpha = 0.64f))
        else -> listOf(SoftAmber.copy(alpha = 0.80f), SoftSand.copy(alpha = 0.64f), Color(0xFFF8E7C8).copy(alpha = 0.52f))
    }
}

private fun phaseDurationFor(state: ConversationState): Int {
    return when (state) {
        ConversationState.Listening -> 1800
        ConversationState.Speaking -> 1500
        ConversationState.Thinking, ConversationState.Activating, ConversationState.Connecting -> 3600
        ConversationState.Error -> 4200
        else -> 5200
    }
}

private const val PI2 = 6.2831855f
