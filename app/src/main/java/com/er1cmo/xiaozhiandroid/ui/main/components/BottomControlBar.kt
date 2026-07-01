package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.domain.VoiceInteractionMode
import com.er1cmo.xiaozhiandroid.ui.theme.CharcoalBlack
import com.er1cmo.xiaozhiandroid.ui.theme.CharcoalPressed
import com.er1cmo.xiaozhiandroid.ui.theme.WarmCardWhite
import com.er1cmo.xiaozhiandroid.ui.theme.WarmLine

@Composable
fun BottomControlBar(
    textInput: String,
    isManualMode: Boolean,
    voiceMode: VoiceInteractionMode,
    isListening: Boolean,
    vadStatus: String,
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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = WarmCardWhite,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoiceActionButton(
                    mode = voiceMode,
                    isListening = isListening,
                    modifier = Modifier.weight(1.35f),
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                )
                MinimalOutlineButton(
                    text = "打断",
                    modifier = Modifier.weight(0.74f),
                    onClick = onAbort,
                )
                MinimalOutlineButton(
                    text = voiceMode.wireName,
                    modifier = Modifier.weight(0.86f),
                    onClick = onToggleManualMode,
                )
            }

            Text(
                text = when (voiceMode) {
                    VoiceInteractionMode.Manual -> "MANUAL：按住说话，松开后发送"
                    VoiceInteractionMode.AutoStop -> vadStatus
                    VoiceInteractionMode.Realtime -> vadStatus
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = textInput,
                    onValueChange = onTextChanged,
                    placeholder = { Text("输入文字与小智对话") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CharcoalBlack.copy(alpha = 0.62f),
                        unfocusedBorderColor = WarmLine,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        cursorColor = CharcoalBlack,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendText() }),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onSendText,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalBlack,
                        contentColor = WarmCardWhite,
                    ),
                ) {
                    Text("发送", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MinimalOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.height(46.dp),
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, WarmLine),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WarmCardWhite,
            contentColor = CharcoalBlack,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun VoiceActionButton(
    mode: VoiceInteractionMode,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed || isListening) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "voice_action_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed || isListening) CharcoalPressed else CharcoalBlack,
        animationSpec = tween(durationMillis = 140),
        label = "voice_action_color",
    )

    val label = when {
        mode == VoiceInteractionMode.Manual -> "按住说话"
        isListening -> "停止收音"
        mode == VoiceInteractionMode.AutoStop -> "自然对话"
        mode == VoiceInteractionMode.Realtime -> "全双工"
        else -> "开始"
    }

    val baseModifier = modifier
        .height(46.dp)
        .scale(scale)

    val gestureModifier = if (mode == VoiceInteractionMode.Manual) {
        baseModifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onStartListening()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        onStopListening()
                    }
                },
            )
        }
    } else {
        baseModifier.clickable {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            if (isListening) {
                onStopListening()
            } else {
                onStartListening()
            }
        }
    }

    Surface(
        modifier = gestureModifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = WarmCardWhite,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
