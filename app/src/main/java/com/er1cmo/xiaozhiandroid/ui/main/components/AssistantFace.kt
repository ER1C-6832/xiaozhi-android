package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.ConversationState

@Composable
fun AssistantFace(
    state: ConversationState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF176),
                                Color(0xFFFFC107),
                                Color(0xFFFF9800),
                            ),
                        ),
                    )
                    drawCircle(
                        color = Color(0x66EF6C00),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    drawLine(
                        color = Color(0xFF3E2C00),
                        start = Offset(size.width * 0.28f, size.height * 0.44f),
                        end = Offset(size.width * 0.42f, size.height * 0.44f),
                        strokeWidth = 9.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0xFF3E2C00),
                        start = Offset(size.width * 0.60f, size.height * 0.44f),
                        end = Offset(size.width * 0.74f, size.height * 0.44f),
                        strokeWidth = 9.dp.toPx(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
