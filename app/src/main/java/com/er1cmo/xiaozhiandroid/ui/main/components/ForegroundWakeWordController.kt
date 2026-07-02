package com.er1cmo.xiaozhiandroid.ui.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.er1cmo.xiaozhiandroid.wakeword.SherpaWakeWordEngine
import com.er1cmo.xiaozhiandroid.wakeword.WakeWordConfig
import com.er1cmo.xiaozhiandroid.wakeword.WakeWordEvent

/**
 * Phase 12A real foreground wake-word controller.
 *
 * The old Android SpeechRecognizer bridge is intentionally removed. It depended on
 * ROM/cloud recognizer state and produced network/language errors plus system prompt
 * sounds. This controller uses a real local sherpa-onnx KWS pipeline:
 *
 * AudioRecord 16 kHz PCM -> KeywordSpotter -> WakeWordEvent.
 */
@Composable
fun ForegroundWakeWordController(
    enabled: Boolean,
    keyword: String,
    shouldListen: Boolean,
    onDetected: (String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val latestOnDetected by rememberUpdatedState(onDetected)
    val latestOnStatus by rememberUpdatedState(onStatus)
    val normalizedKeyword = keyword.trim().ifBlank { WakeWordConfig.DEFAULT_KEYWORD }
    val engine = remember(context) {
        SherpaWakeWordEngine(context = context) { event ->
            when (event) {
                is WakeWordEvent.Detected -> latestOnDetected(event.rawKeyword)
                is WakeWordEvent.Status -> latestOnStatus(event.message)
                is WakeWordEvent.Error -> latestOnStatus(event.message)
            }
        }
    }

    LaunchedEffect(enabled, normalizedKeyword, shouldListen) {
        engine.updateKeyword(normalizedKeyword)
        if (enabled && shouldListen) {
            engine.start()
        } else {
            engine.stop(
                reason = when {
                    !enabled -> "本地唤醒词已关闭"
                    !shouldListen -> "当前状态暂停本地唤醒词监听"
                    else -> "暂停本地唤醒词监听"
                },
            )
        }
    }

    DisposableEffect(engine) {
        onDispose {
            engine.release()
        }
    }
}
