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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.McpToolCallStatus
import com.er1cmo.xiaozhiandroid.domain.McpToolCallUiState

@Composable
fun ToolCallPanel(
    toolCalls: List<McpToolCallUiState>,
    lastMcpStatus: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "工具调用卡片",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = lastMcpStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "最近 ${toolCalls.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (toolCalls.isEmpty()) {
                Text(
                    text = "等待服务端发起 tools/call。工具执行后会显示工具名、参数、结果、耗时与成功/失败。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                toolCalls.forEach { call ->
                    ToolCallCard(call = call)
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(call: McpToolCallUiState) {
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = call.toolName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = call.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
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
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}
