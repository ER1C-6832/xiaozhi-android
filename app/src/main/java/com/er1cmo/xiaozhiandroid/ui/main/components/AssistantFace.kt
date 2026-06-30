package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 10 Assistant Orb.
 *
 * The old static face has been replaced by a small state-life-form: calm when
 * connected, wave rings while listening, flowing arcs while thinking, mouth/wave
 * bars while speaking, and a red blink on errors.
 */
@Composable
fun AssistantFace(
    state: ConversationState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant-orb")
    val slow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "slow-phase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val blink by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "error-blink",
    )

    val stateLine = when (state) {
        ConversationState.Idle -> "点击连接，让小智上线"
        ConversationState.Connected -> "已准备好，可以说话或输入"
        ConversationState.Listening -> "我在听"
        ConversationState.Thinking -> "正在理解上下文"
        ConversationState.Speaking -> "正在回复你"
        ConversationState.Activating -> "正在完成激活"
        ConversationState.Connecting -> "正在建立安全连接"
        ConversationState.Error -> "出现异常，请查看开发者抽屉"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val minSide = size.minDimension
                    val baseRadius = minSide * 0.28f
                    val activeColor = when (state) {
                        ConversationState.Idle -> Color(0xFF7B8DA6)
                        ConversationState.Connected -> Color(0xFF00D5FF)
                        ConversationState.Listening -> Color(0xFF00E5FF)
                        ConversationState.Thinking -> Color(0xFF8A5CFF)
                        ConversationState.Speaking -> Color(0xFFFF6BD6)
                        ConversationState.Activating,
                        ConversationState.Connecting -> Color(0xFF3F6BFF)
                        ConversationState.Error -> Color(0xFFFF4F7A)
                    }
                    val glowAlpha = when (state) {
                        ConversationState.Error -> blink * 0.42f
                        ConversationState.Idle -> 0.18f
                        else -> 0.30f + slow * 0.16f
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = glowAlpha),
                                activeColor.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = minSide * 0.50f,
                        ),
                        radius = minSide * 0.50f,
                        center = center,
                    )

                    if (state == ConversationState.Listening) {
                        repeat(3) { index ->
                            val progress = (slow + index * 0.28f) % 1f
                            drawCircle(
                                color = activeColor.copy(alpha = (1f - progress) * 0.45f),
                                radius = baseRadius + progress * minSide * 0.22f,
                                center = center,
                                style = Stroke(width = (2.4f + index).dp.toPx()),
                            )
                        }
                    }

                    if (state == ConversationState.Thinking || state == ConversationState.Connecting || state == ConversationState.Activating) {
                        repeat(3) { index ->
                            val start = slow * 360f + index * 120f
                            drawArc(
                                color = activeColor.copy(alpha = 0.75f - index * 0.13f),
                                startAngle = start,
                                sweepAngle = 64f,
                                useCenter = false,
                                topLeft = Offset(center.x - baseRadius * (1.15f + index * 0.20f), center.y - baseRadius * (1.15f + index * 0.20f)),
                                size = androidx.compose.ui.geometry.Size(
                                    baseRadius * 2f * (1.15f + index * 0.20f),
                                    baseRadius * 2f * (1.15f + index * 0.20f),
                                ),
                                style = Stroke(width = 4.dp.toPx()),
                            )
                        }
                    }

                    val orbScale = when (state) {
                        ConversationState.Speaking,
                        ConversationState.Listening -> pulse
                        ConversationState.Error -> 0.96f + blink * 0.08f
                        else -> 1f + (slow - 0.5f) * 0.04f
                    }
                    val orbRadius = baseRadius * orbScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.92f),
                                activeColor.copy(alpha = 0.92f),
                                Color(0xFF07111F).copy(alpha = 0.92f),
                            ),
                            center = Offset(center.x - orbRadius * 0.24f, center.y - orbRadius * 0.28f),
                            radius = orbRadius * 1.55f,
                        ),
                        radius = orbRadius,
                        center = center,
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.78f),
                        radius = orbRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx()),
                    )

                    when (state) {
                        ConversationState.Speaking -> {
                            val bars = 7
                            val gap = 9.dp.toPx()
                            val startX = center.x - gap * (bars - 1) / 2f
                            repeat(bars) { index ->
                                val wave = sin((slow * 2f * PI + index * 0.75f)).toFloat()
                                val height = 12.dp.toPx() + (wave + 1f) * 14.dp.toPx()
                                val x = startX + index * gap
                                drawLine(
                                    color = Color.White.copy(alpha = 0.86f),
                                    start = Offset(x, center.y + 14.dp.toPx() - height / 2f),
                                    end = Offset(x, center.y + 14.dp.toPx() + height / 2f),
                                    strokeWidth = 4.dp.toPx(),
                                )
                            }
                        }
                        ConversationState.Listening -> {
                            repeat(9) { index ->
                                val angle = (index / 9f) * 2f * PI.toFloat()
                                val wave = sin((slow * 2f * PI.toFloat() + index).toDouble()).toFloat().coerceIn(-1f, 1f)
                                val radius = orbRadius * (0.76f + wave * 0.06f)
                                val dot = Offset(
                                    center.x + cos(angle) * radius,
                                    center.y + sin(angle) * radius,
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.72f),
                                    radius = 2.6.dp.toPx(),
                                    center = dot,
                                )
                            }
                        }
                        ConversationState.Error -> {
                            drawLine(
                                color = Color.White.copy(alpha = 0.92f),
                                start = Offset(center.x - 18.dp.toPx(), center.y - 6.dp.toPx()),
                                end = Offset(center.x + 18.dp.toPx(), center.y + 6.dp.toPx()),
                                strokeWidth = 5.dp.toPx(),
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.92f),
                                start = Offset(center.x + 18.dp.toPx(), center.y - 6.dp.toPx()),
                                end = Offset(center.x - 18.dp.toPx(), center.y + 6.dp.toPx()),
                                strokeWidth = 5.dp.toPx(),
                            )
                        }
                        else -> {
                            val y = center.y + 14.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.78f),
                                start = Offset(center.x - 22.dp.toPx(), y),
                                end = Offset(center.x + 22.dp.toPx(), y),
                                strokeWidth = 5.dp.toPx(),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
