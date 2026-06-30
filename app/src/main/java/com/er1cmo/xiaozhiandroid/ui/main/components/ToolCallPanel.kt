package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.McpToolCallStatus
import com.er1cmo.xiaozhiandroid.domain.McpToolCallUiState
import kotlinx.coroutines.delay

/**
 * Transient floating MCP card.
 *
 * Phase 9 initially rendered tool calls as a persistent main-screen panel. The
 * product behavior is now closer to a toast/card: show the latest call on top of
 * the main screen, keep it visible while running, and hide it a few seconds after
 * it reaches a final state. Full history stays in Settings.
 */
@Composable
fun ToolCallPanel(
    toolCalls: List<McpToolCallUiState>,
    lastMcpStatus: String,
    modifier: Modifier = Modifier,
) {
    val latestCall = toolCalls.firstOrNull()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(
        latestCall?.requestId,
        latestCall?.status,
        latestCall?.resultPreview,
    ) {
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
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
                        text = "正在使用本机工具",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = latestCall.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = latestCall.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(latestCall.status),
                )
            }
            Text(
                text = lastMcpStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(containerColor, RoundedCornerShape(14.dp))
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
