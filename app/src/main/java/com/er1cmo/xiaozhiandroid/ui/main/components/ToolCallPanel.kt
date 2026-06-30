package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.McpToolCallStatus
import com.er1cmo.xiaozhiandroid.domain.McpToolCallUiState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Transient floating MCP card.
 *
 * Shows only the latest tool call on top of the main screen. It auto-hides after
 * a final state and can also be dismissed manually by swiping left or upward.
 * Full history is kept in Settings.
 */
@Composable
fun ToolCallPanel(
    toolCalls: List<McpToolCallUiState>,
    lastMcpStatus: String,
    modifier: Modifier = Modifier,
) {
    val latestCall = toolCalls.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    var offsetX by remember(latestCall?.requestId) { mutableStateOf(0f) }
    var offsetY by remember(latestCall?.requestId) { mutableStateOf(0f) }

    LaunchedEffect(
        latestCall?.requestId,
        latestCall?.status,
        latestCall?.resultPreview,
    ) {
        offsetX = 0f
        offsetY = 0f
        if (latestCall == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (latestCall.status != McpToolCallStatus.Running) {
            delay(AUTO_HIDE_DELAY_MS)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && latestCall != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        latestCall?.let { call ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .pointerInput(call.requestId) {
                        detectDragGestures(
                            onDrag = { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y.coerceAtMost(14f)
                            },
                            onDragEnd = {
                                val shouldDismiss = offsetX < -SWIPE_DISMISS_X || offsetY < -SWIPE_DISMISS_Y || abs(offsetX) > SWIPE_DISMISS_STRONG
                                if (shouldDismiss) {
                                    visible = false
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            onDragCancel = {
                                offsetX = 0f
                                offsetY = 0f
                            },
                        )
                    },
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    cardTint(call.status),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                ),
                            ),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本机工具调用",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = call.toolName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = call.status.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor(call.status),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = lastMcpStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FloatingToolCallCard(call = call)
                    Text(
                        text = "上滑或左滑可关闭，完整历史在设置页查看。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingToolCallCard(call: McpToolCallUiState) {
    val containerColor = when (call.status) {
        McpToolCallStatus.Running -> MaterialTheme.colorScheme.primaryContainer
        McpToolCallStatus.Succeeded -> MaterialTheme.colorScheme.secondaryContainer
        McpToolCallStatus.Failed -> MaterialTheme.colorScheme.errorContainer
        McpToolCallStatus.Blocked -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (call.status) {
        McpToolCallStatus.Running -> MaterialTheme.colorScheme.onPrimaryContainer
        McpToolCallStatus.Succeeded -> MaterialTheme.colorScheme.onSecondaryContainer
        McpToolCallStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        McpToolCallStatus.Blocked -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "id=${call.requestId}  ${call.startedAtText}" + call.durationMs?.let { "  ${it}ms" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        if (call.argumentsPreview.isNotBlank() && call.argumentsPreview != "{}") {
            ToolMonospaceLine(label = "参数", value = call.argumentsPreview, color = contentColor)
        }
        ToolMonospaceLine(label = "结果", value = call.resultPreview, color = contentColor)
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun statusColor(status: McpToolCallStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        McpToolCallStatus.Running -> MaterialTheme.colorScheme.primary
        McpToolCallStatus.Succeeded -> MaterialTheme.colorScheme.secondary
        McpToolCallStatus.Failed -> MaterialTheme.colorScheme.error
        McpToolCallStatus.Blocked -> MaterialTheme.colorScheme.tertiary
    }
}

@Composable
private fun cardTint(status: McpToolCallStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        McpToolCallStatus.Running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        McpToolCallStatus.Succeeded -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
        McpToolCallStatus.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        McpToolCallStatus.Blocked -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
    }
}

@Composable
private fun ToolMonospaceLine(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val AUTO_HIDE_DELAY_MS = 4_500L
private const val SWIPE_DISMISS_X = 72f
private const val SWIPE_DISMISS_Y = 58f
private const val SWIPE_DISMISS_STRONG = 170f
