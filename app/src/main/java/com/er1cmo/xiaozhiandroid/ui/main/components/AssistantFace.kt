package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.ui.theme.ColorLilac
import com.er1cmo.xiaozhiandroid.ui.theme.ColorMistyBlue
import com.er1cmo.xiaozhiandroid.ui.theme.ColorPeach
import com.er1cmo.xiaozhiandroid.ui.theme.ColorRoseRed
import com.er1cmo.xiaozhiandroid.ui.theme.ColorSlateGray
import com.er1cmo.xiaozhiandroid.ui.theme.ColorSunsetAmber
import com.er1cmo.xiaozhiandroid.ui.theme.WarmCardWhite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Aurora Fluid Gradient assistant visual.
 *
 * It deliberately contains no eyes, mouth, radar rings, strokes or mechanical
 * outlines. The assistant is represented by saturated, feathered radial
 * gradients layered with SrcOver so the colors stay vivid on a white card.
 */
@Composable
fun AssistantFace(
    state: ConversationState,
    modifier: Modifier = Modifier,
    volumeScale: Float = 0f,
) {
    val target = remember(state) { state.toAuroraTarget() }
    val transitionSpec = tween<Float>(durationMillis = 1_000, easing = FastOutSlowInEasing)
    val colorTransitionSpec = tween<Color>(durationMillis = 1_000, easing = FastOutSlowInEasing)

    val globalAlpha by animateFloatAsState(
        targetValue = target.globalAlpha,
        animationSpec = transitionSpec,
        label = "aurora_global_alpha",
    )
    val fluidScale by animateFloatAsState(
        targetValue = target.fluidScale,
        animationSpec = transitionSpec,
        label = "aurora_fluid_scale",
    )
    val animationSpeedScale by animateFloatAsState(
        targetValue = target.animationSpeedScale,
        animationSpec = transitionSpec,
        label = "aurora_animation_speed_scale",
    )
    val colorBlendRatio by animateFloatAsState(
        targetValue = target.colorBlendRatio,
        animationSpec = transitionSpec,
        label = "aurora_color_blend_ratio",
    )
    val animatedColorA by animateColorAsState(
        targetValue = target.colorA,
        animationSpec = colorTransitionSpec,
        label = "aurora_color_a",
    )
    val animatedColorB by animateColorAsState(
        targetValue = target.colorB,
        animationSpec = colorTransitionSpec,
        label = "aurora_color_b",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aurora_time_drive")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora_time",
    )
    val rotatingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora_rotating_angle",
    )

    val syntheticVolumeScale = when (state) {
        ConversationState.Listening -> (0.18f + 0.82f * abs(sin(time * 2.7f))).coerceIn(0f, 1f)
        ConversationState.Speaking -> (0.22f + 0.78f * abs(sin(time * 3.1f + 0.6f))).coerceIn(0f, 1f)
        else -> 0f
    }
    val reactiveVolumeScale = if (volumeScale > 0f) {
        volumeScale.coerceIn(0f, 1f)
    } else {
        syntheticVolumeScale
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = WarmCardWhite,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.25f
                val d35 = 35.dp.toPx()
                val d30 = 30.dp.toPx()
                val d25 = 25.dp.toPx()
                val orbitRadius = min(size.width, size.height) * 0.20f

                val speakingScale = if (state == ConversationState.Speaking) {
                    1f + reactiveVolumeScale * 0.15f
                } else {
                    1f
                }
                val effectiveFluidScale = fluidScale * speakingScale
                val colorA = blendAuroraColor(ColorSlateGray.copy(alpha = 0.75f), animatedColorA, colorBlendRatio)
                val colorB = blendAuroraColor(ColorSlateGray.copy(alpha = 0.46f), animatedColorB, colorBlendRatio)
                val listeningPerturb = if (state == ConversationState.Listening) reactiveVolumeScale * 0.35f else 0f

                val radiusA = baseRadius * effectiveFluidScale *
                    (1.0f + 0.15f * sin(time * 1.0f * animationSpeedScale) + listeningPerturb)
                val radiusB = baseRadius * effectiveFluidScale *
                    (0.9f + 0.12f * cos(time * 1.3f * animationSpeedScale) + listeningPerturb)

                val defaultOffsetA = center + Offset(
                    x = cos(time * 0.8f * animationSpeedScale) * d35,
                    y = sin(time * 1.2f * animationSpeedScale) * d25,
                )
                val defaultOffsetB = center + Offset(
                    x = sin(time * 1.1f * animationSpeedScale) * -d30,
                    y = cos(time * 0.9f * animationSpeedScale) * d30,
                )

                val orbitAngle = Math.toRadians(rotatingAngle.toDouble()).toFloat()
                val thinkingOffsetA = center + Offset(
                    x = cos(orbitAngle) * orbitRadius,
                    y = sin(orbitAngle) * orbitRadius,
                )
                val thinkingOffsetB = center + Offset(
                    x = cos(orbitAngle + PI.toFloat()) * orbitRadius,
                    y = sin(orbitAngle + PI.toFloat()) * orbitRadius,
                )

                val centerA = if (state == ConversationState.Thinking) thinkingOffsetA else defaultOffsetA
                val centerB = if (state == ConversationState.Thinking) thinkingOffsetB else defaultOffsetB

                drawFeatheredAuroraBlob(
                    color = colorA,
                    center = centerA,
                    radius = radiusA,
                    globalAlpha = globalAlpha,
                )
                drawFeatheredAuroraBlob(
                    color = colorB,
                    center = centerB,
                    radius = radiusB,
                    globalAlpha = globalAlpha,
                )

                if (state == ConversationState.Speaking) {
                    val radiusC = baseRadius * (0.6f + reactiveVolumeScale * 0.4f)
                    val centerC = center + Offset(
                        x = 0f,
                        y = 25.dp.toPx() + reactiveVolumeScale * 55.dp.toPx(),
                    )
                    drawFeatheredAuroraBlob(
                        color = ColorSunsetAmber.copy(alpha = 0.8f),
                        center = centerC,
                        radius = radiusC,
                        globalAlpha = globalAlpha,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFeatheredAuroraBlob(
    color: Color,
    center: Offset,
    radius: Float,
    globalAlpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = (color.alpha * globalAlpha).coerceIn(0f, 1f)),
                color.copy(alpha = (color.alpha * globalAlpha * 0.58f).coerceIn(0f, 1f)),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.SrcOver,
    )
}

private fun ConversationState.toAuroraTarget(): AuroraTarget {
    return when (this) {
        ConversationState.Idle -> AuroraTarget(
            globalAlpha = 0.8f,
            fluidScale = 0.9f,
            animationSpeedScale = 0.15f,
            colorBlendRatio = 0f,
            colorA = ColorSlateGray.copy(alpha = 0.75f),
            colorB = ColorSlateGray.copy(alpha = 0.46f),
        )
        ConversationState.Connected -> AuroraTarget(
            globalAlpha = 0.95f,
            fluidScale = 1.0f,
            animationSpeedScale = 0.5f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue.copy(alpha = 0.85f),
            colorB = ColorLilac.copy(alpha = 0.8f),
        )
        ConversationState.Listening -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.1f,
            animationSpeedScale = 2.0f,
            colorBlendRatio = 1f,
            colorA = ColorPeach.copy(alpha = 0.85f),
            colorB = ColorMistyBlue.copy(alpha = 0.85f),
        )
        ConversationState.Thinking -> AuroraTarget(
            globalAlpha = 0.95f,
            fluidScale = 1.05f,
            animationSpeedScale = 1.8f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue.copy(alpha = 0.85f),
            colorB = ColorLilac.copy(alpha = 0.8f),
        )
        ConversationState.Speaking -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.0f,
            animationSpeedScale = 1.2f,
            colorBlendRatio = 1f,
            colorA = ColorPeach.copy(alpha = 0.85f),
            colorB = ColorLilac.copy(alpha = 0.8f),
        )
        ConversationState.Error -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 0.9f,
            animationSpeedScale = 0.4f,
            colorBlendRatio = 1f,
            colorA = ColorRoseRed.copy(alpha = 0.9f),
            colorB = ColorSlateGray.copy(alpha = 0.75f),
        )
        ConversationState.Activating,
        ConversationState.Connecting -> AuroraTarget(
            globalAlpha = 0.95f,
            fluidScale = 0.98f,
            animationSpeedScale = 1.0f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue.copy(alpha = 0.85f),
            colorB = ColorLilac.copy(alpha = 0.8f),
        )
    }
}

private fun blendAuroraColor(
    from: Color,
    to: Color,
    ratio: Float,
): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}

private data class AuroraTarget(
    val globalAlpha: Float,
    val fluidScale: Float,
    val animationSpeedScale: Float,
    val colorBlendRatio: Float,
    val colorA: Color,
    val colorB: Color,
)

private const val TWO_PI = (2.0 * PI).toFloat()
