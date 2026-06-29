package com.er1cmo.xiaozhiandroid.audio

import android.Manifest
import android.media.AudioRecord
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioEngine(
    private val appScope: CoroutineScope,
) {
    @Volatile
    private var recording = false

    private var recordingJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null

    fun isRecording(): Boolean = recording

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(
        onEncodedFrame: (ByteArray) -> Boolean,
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ) {
        if (recording) {
            onLog("音频上行已在运行，忽略重复启动")
            return
        }

        recording = true
        onStatusChanged("录音启动中")
        recordingJob = appScope.launch(Dispatchers.IO) {
            val recorder = AudioRecorder()
            var audioRecord: AudioRecord? = null
            var encoder: OpusEncoder? = null
            var pcmFrames = 0
            var opusPackets = 0
            try {
                encoder = OpusEncoder()
                audioRecord = recorder.createAudioRecord()
                activeAudioRecord = audioRecord
                audioRecord.startRecording()
                onLog(
                    "音频上行启动：${AudioConstants.INPUT_SAMPLE_RATE}Hz/mono/" +
                        "${AudioConstants.FRAME_DURATION_MS}ms，PCM ${AudioConstants.PCM_FRAME_BYTES}B/帧",
                )
                onStatusChanged("录音中")

                val buffer = ByteArray(AudioConstants.PCM_FRAME_BYTES)
                while (recording) {
                    val ok = recorder.readPcmFrame(audioRecord, buffer)
                    if (!ok || !recording) break

                    val pcm = buffer.copyOf()
                    val presentationTimeUs = pcmFrames * AudioConstants.FRAME_DURATION_MS * 1_000L
                    pcmFrames += 1
                    val packets = encoder.encode(PcmFrame(pcm, presentationTimeUs))
                    for (packet in packets) {
                        if (packet.isEmpty()) continue
                        val sent = onEncodedFrame(packet)
                        if (!sent) {
                            onLog("Opus 音频帧发送失败，停止录音上行")
                            recording = false
                            break
                        }
                        opusPackets += 1
                    }

                    if (opusPackets > 0 && opusPackets % LOG_INTERVAL_PACKETS == 0) {
                        onStatusChanged("上行中，已发送 ${opusPackets} 帧")
                        onLog("已发送 Opus 音频帧：${opusPackets}")
                    }
                }
            } catch (exception: Exception) {
                onStatusChanged("录音失败")
                onLog("音频上行异常：${exception.message ?: exception::class.java.simpleName}")
            } finally {
                recording = false
                activeAudioRecord = null
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
                runCatching { encoder?.release() }
                onStatusChanged("已停止，累计发送 ${opusPackets} 帧")
                onLog("音频上行停止：PCM ${pcmFrames} 帧，Opus ${opusPackets} 帧")
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        runCatching { activeAudioRecord?.stop() }
    }

    fun release() {
        stopRecording()
        recordingJob?.cancel()
        recordingJob = null
    }

    private companion object {
        const val LOG_INTERVAL_PACKETS = 50
    }
}
