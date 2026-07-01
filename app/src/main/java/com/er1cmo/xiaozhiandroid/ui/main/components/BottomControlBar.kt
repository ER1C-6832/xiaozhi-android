package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.er1cmo.xiaozhiandroid.ui.theme.Charcoal
import com.er1cmo.xiaozhiandroid.ui.theme.CharcoalPressed
import com.er1cmo.xiaozhiandroid.ui.theme.OatBackground
import com.er1cmo.xiaozhiandroid.ui.theme.WarmBorder
import com.er1cmo.xiaozhiandroid.ui.theme.WarmSurface
import com.er1cmo.xiaozhiandroid.ui.theme.WarmText
import com.er1cmo.xiaozhiandroid.ui.theme.WarmTextSecondary

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
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = WarmSurface,
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
                HoldToTalkButton(
                    modifier = Modifier.weight(1.35f),
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                )
                MinimalAssistButton(
                    text = "打断",
                    onClick = onAbort,
                    modifier = Modifier.weight(0.82f),
                )
                MinimalAssistButton(
                    text = if (isManualMode) "手动" else "自动",
                    onClick = onToggleManualMode,
                    modifier = Modifier.weight(0.82f),
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
                    placeholder = {
                        Text(
                            text = "输入文字，或者按住说话",
                            color = WarmTextSecondary,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OatBackground,
                        unfocusedContainerColor = OatBackground,
                        disabledContainerColor = OatBackground,
                        focusedBorderColor = WarmTextSecondary.copy(alpha = 0.42f),
                        unfocusedBorderColor = WarmBorder,
                        focusedTextColor = WarmText,
                        unfocusedTextColor = WarmText,
                        cursorColor = WarmText,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendText() }),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = onSendText,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Charcoal,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("发送", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MinimalAssistButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, WarmBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WarmSurface,
            contentColor = WarmText,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun HoldToTalkButton(
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = 110),
    )
    val haptic = LocalHapticFeedback.current
    val buttonColor = if (pressed) CharcoalPressed else Charcoal

    Surface(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartListening()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onStopListening()
                            pressed = false
                        }
                    },
                )
            },
        shape = RoundedCornerShape(999.dp),
        color = buttonColor,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = if (pressed) 0.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(buttonColor)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "按住说话",
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}
