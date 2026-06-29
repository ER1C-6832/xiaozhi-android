package com.er1cmo.xiaozhiandroid.ui.main.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoldToTalkButton(
                    modifier = Modifier.weight(1f),
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                )
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onAbort,
                ) {
                    Text("打断对话")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onToggleManualMode,
                ) {
                    Text(if (isManualMode) "手动对话" else "自动对话")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = textInput,
                    onValueChange = onTextChanged,
                    placeholder = { Text("输入文字测试服务端连通性") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendText() }),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onSendText) {
                    Text("发送")
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
    Surface(
        modifier = modifier
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStartListening()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onStopListening()
                        }
                    },
                )
            },
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("按住后说话")
        }
    }
}
