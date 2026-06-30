package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun BottomControlBar(
    textInput: String,
    isManualMode: Boolean,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onAbort: () -> Unit,
    onToggleManualMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoldToTalkButton(
                    modifier = Modifier.weight(1.25f),
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                )
                PillActionButton(
                    text = "打断",
                    modifier = Modifier.weight(0.85f),
                    danger = true,
                    onClick = onAbort,
                )
                PillActionButton(
                    text = if (isManualMode) "手动" else "自动",
                    modifier = Modifier.weight(0.85f),
                    onClick = onToggleManualMode,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = textInput,
                    onValueChange = onTextChanged,
                    placeholder = { Text("输入文字，或按住说话") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendText() }),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    onClick = onSendText,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("发送", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldToTalkButton(
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (pressed) 0.965f else 1f, label = "hold-scale")

    Surface(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartListening()
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onStopListening()
                        }
                    },
                )
            },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = if (pressed) 2.dp else 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                )
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (pressed) "正在聆听" else "按住说话",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PillActionButton(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        ),
        onClick = onClick,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
