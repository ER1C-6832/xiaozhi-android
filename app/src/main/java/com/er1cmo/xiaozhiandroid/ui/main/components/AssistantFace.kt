package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * The visual intentionally contains no face, eyes, mouth, radar rings, strokes or
 * mechanical geometry. A monotonic frame-time drive is used instead of a
 * repeating 0..2PI tween so slow states such as Connected/Ready never snap back
 * after a half cycle.
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

    val elapsedSeconds by produceState(initialValue = 0f) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                value = ((frameNanos - startNanos) / 1_000_000_000f).coerceAtLeast(0f)
            }
        }
    }

    val basePhase = elapsedSeconds * TWO_PI / BASE_CYCLE_SECONDS
    val fluidPhase = basePhase * animationSpeedScale
    val rotatingAngleRadians = elapsedSeconds * TWO_PI / THINKING_ORBIT_SECONDS

    val syntheticVolumeScale = when (state) {
        ConversationState.Listening -> (0.18f + 0.82f * abs(sin(elapsedSeconds * 2.7f))).coerceIn(0f, 1f)
        ConversationState.Speaking -> (0.22f + 0.78f * abs(sin(elapsedSeconds * 3.1f + 0.6f))).coerceIn(0f, 1f)
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
            Canvas(modifier = Modifier.size(260.dp)) {
                val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.25f
                val d35 = 35.dp.toPx()
                val d30 = 30.dp.toPx()
                val d25 = 25.dp.toPx()
                val d55 = 55.dp.toPx()
                val orbitRadius = min(size.width, size.height) * 0.20f
                val effectiveFluidScale = if (state == ConversationState.Speaking) {
                    fluidScale * (1f + reactiveVolumeScale * 0.15f)
                } else {
                    fluidScale
                }

                val colorA = blendAuroraColor(ColorSlateGray, animatedColorA, colorBlendRatio)
                val colorB = blendAuroraColor(ColorSlateGray.copy(alpha = 0.55f), animatedColorB, colorBlendRatio)
                val listeningPerturb = if (state == ConversationState.Listening) reactiveVolumeScale * 0.35f else 0f

                val radiusA = baseRadius * effectiveFluidScale *
                    (1.0f + 0.15f * sin(fluidPhase * 1.0f) + listeningPerturb)
                val radiusB = baseRadius * effectiveFluidScale *
                    (0.9f + 0.12f * cos(fluidPhase * 1.3f) + listeningPerturb)

                val defaultOffsetA = canvasCenter + Offset(
                    x = cos(fluidPhase * 0.80f) * d35,
                    y = sin(fluidPhase * 0.80f) * d25,
                )
                val defaultOffsetB = canvasCenter + Offset(
                    x = sin(fluidPhase * 0.90f + 1.35f) * -d30,
                    y = cos(fluidPhase * 0.90f + 1.35f) * d30,
                )

                val thinkingOffsetA = canvasCenter + Offset(
                    x = cos(rotatingAngleRadians) * orbitRadius,
                    y = sin(rotatingAngleRadians) * orbitRadius,
                )
                val thinkingOffsetB = canvasCenter + Offset(
                    x = cos(rotatingAngleRadians + PI.toFloat()) * orbitRadius,
                    y = sin(rotatingAngleRadians + PI.toFloat()) * orbitRadius,
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
                    val radiusC = baseRadius * effectiveFluidScale * (0.6f + reactiveVolumeScale * 0.4f)
                    val centerC = canvasCenter + Offset(
                        x = 0f,
                        y = d25 + reactiveVolumeScale * d55,
                    )
                    drawFeatheredAuroraBlob(
                        color = ColorSunsetAmber,
                        center = centerC,
                        radius = radiusC,
                        globalAlpha = globalAlpha * 0.92f,
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
    val centerAlpha = auroraCenterAlpha(color) * globalAlpha
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = centerAlpha.coerceIn(0f, 1f)),
                color.copy(alpha = (centerAlpha * 0.48f).coerceIn(0f, 1f)),
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

private fun auroraCenterAlpha(color: Color): Float {
    return when {
        color.sameRgb(ColorMistyBlue) -> 0.85f
        color.sameRgb(ColorLilac) -> 0.80f
        color.sameRgb(ColorPeach) -> 0.85f
        color.sameRgb(ColorSlateGray) -> min(color.alpha, 0.75f)
        color.sameRgb(ColorSunsetAmber) -> 0.80f
        color.sameRgb(ColorRoseRed) -> 0.90f
        else -> color.alpha.coerceIn(0.80f, 0.90f)
    }
}

private fun Color.sameRgb(other: Color): Boolean {
    return abs(red - other.red) < 0.002f &&
        abs(green - other.green) < 0.002f &&
        abs(blue - other.blue) < 0.002f
}

private fun ConversationState.toAuroraTarget(): AuroraTarget {
    return when (this) {
        ConversationState.Idle -> AuroraTarget(
            globalAlpha = 0.8f,
            fluidScale = 0.9f,
            animationSpeedScale = 0.15f,
            colorBlendRatio = 0f,
            colorA = ColorSlateGray,
            colorB = ColorSlateGray.copy(alpha = 0.55f),
        )
        ConversationState.Connected -> AuroraTarget(
            globalAlpha = 0.95f,
            fluidScale = 1.0f,
            animationSpeedScale = 0.5f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorLilac,
        )
        ConversationState.Listening -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.1f,
            animationSpeedScale = 2.0f,
            colorBlendRatio = 1f,
            colorA = ColorPeach,
            colorB = ColorMistyBlue,
        )
        ConversationState.Thinking -> AuroraTarget(
            globalAlpha = 0.95f,
            fluidScale = 1.05f,
            animationSpeedScale = 1.8f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorLilac,
        )
        ConversationState.Speaking -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.0f,
            animationSpeedScale = 1.2f,
            colorBlendRatio = 1f,
            colorA = ColorPeach,
            colorB = ColorLilac,
        )
        ConversationState.Error -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 0.9f,
            animationSpeedScale = 0.4f,
            colorBlendRatio = 1f,
            colorA = ColorRoseRed,
            colorB = ColorSlateGray,
        )
        ConversationState.Activating,
        ConversationState.Connecting -> AuroraTarget(
            globalAlpha = 0.92f,
            fluidScale = 0.98f,
            animationSpeedScale = 1.0f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorSunsetAmber.copy(alpha = 0.82f),
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

private const val BASE_CYCLE_SECONDS = 6f
private const val THINKING_ORBIT_SECONDS = 3.5f
private const val TWO_PI = (2.0 * PI).toFloat()
