package com.er1cmo.xiaozhiandroid.ui.main.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.McpToolCallStatus
import com.er1cmo.xiaozhiandroid.domain.McpToolCallUiState
import com.er1cmo.xiaozhiandroid.ui.theme.Charcoal
import com.er1cmo.xiaozhiandroid.ui.theme.SoftAmber
import com.er1cmo.xiaozhiandroid.ui.theme.SoftClay
import com.er1cmo.xiaozhiandroid.ui.theme.WarmSurface
import com.er1cmo.xiaozhiandroid.ui.theme.WarmText
import com.er1cmo.xiaozhiandroid.ui.theme.WarmTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Transient floating MCP card.
 *
 * The card is intentionally not part of the permanent main layout. It behaves
 * like a warm, lightweight product toast: visible while running, auto-hidden
 * after completion, and dismissible by swiping upward or left.
 */
@Composable
fun ToolCallPanel(
    toolCalls: List<McpToolCallUiState>,
    lastMcpStatus: String,
    modifier: Modifier = Modifier,
) {
    val latestCall = toolCalls.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

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

    if (!visible || latestCall == null) return

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(latestCall.requestId) {
                detectDragGestures(
                    onDrag = { _, dragAmount ->
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    },
                    onDragEnd = {
                        val swipedUp = offsetY < -72f
                        val swipedLeft = offsetX < -96f && abs(offsetX) > abs(offsetY)
                        if (swipedUp || swipedLeft) {
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
            }
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = WarmSurface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本机工具",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmTextSecondary,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = latestCall.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Light,
                        color = WarmText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = latestCall.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(latestCall.status),
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = lastMcpStatus,
                style = MaterialTheme.typography.bodySmall,
                color = WarmTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FloatingToolCallCard(call = latestCall)
        }
    }
}

@Composable
private fun FloatingToolCallCard(call: McpToolCallUiState) {
    val containerColor = when (call.status) {
        McpToolCallStatus.Running -> SoftAmber.copy(alpha = 0.70f)
        McpToolCallStatus.Succeeded -> Color(0xFFEAF1E4)
        McpToolCallStatus.Failed -> SoftClay.copy(alpha = 0.80f)
        McpToolCallStatus.Blocked -> Color(0xFFF0E8D9)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "id=${call.requestId}  ${call.startedAtText}" + call.durationMs?.let { "  ${it}ms" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = WarmTextSecondary,
        )
        if (call.argumentsPreview.isNotBlank() && call.argumentsPreview != "{}") {
            ToolMonospaceLine(label = "参数", value = call.argumentsPreview)
        }
        ToolMonospaceLine(label = "结果", value = call.resultPreview)
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun statusColor(status: McpToolCallStatus): Color {
    return when (status) {
        McpToolCallStatus.Running -> Charcoal
        McpToolCallStatus.Succeeded -> Color(0xFF4A6A45)
        McpToolCallStatus.Failed -> Color(0xFF9C5A50)
        McpToolCallStatus.Blocked -> Color(0xFF8B6B3E)
    }
}

@Composable
private fun ToolMonospaceLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = WarmText,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val AUTO_HIDE_DELAY_MS = 4_500L
