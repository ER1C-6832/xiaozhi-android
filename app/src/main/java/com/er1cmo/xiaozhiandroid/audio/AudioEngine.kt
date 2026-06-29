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

    @Volatile
    private var playbackRunning = false

    @Volatile
    private var playbackGeneration = 0

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null
    private var playbackQueue: PlaybackQueue? = null

    fun isRecording(): Boolean = recording

    fun isPlaybackActive(): Boolean = playbackRunning

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

                    if (opusPackets > 0 && opusPackets % UPLINK_LOG_INTERVAL_PACKETS == 0) {
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

    fun queuePlaybackFrame(
        opusFrame: ByteArray,
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ) {
        val queue = startPlaybackIfNeeded(onLog, onStatusChanged)
        val accepted = queue.offer(opusFrame)
        if (!accepted) {
            onLog("TTS 播放队列已满或已关闭，丢弃 ${opusFrame.size}B 音频帧")
        }
    }

    fun stopPlayback(
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ) {
        val queue = playbackQueue
        if (!playbackRunning && queue == null) {
            onStatusChanged("未播放")
            return
        }

        playbackGeneration += 1
        playbackRunning = false
        playbackQueue = null
        queue?.clear()
        queue?.close()
        playbackJob = null
        onStatusChanged("已停止播放")
        onLog("TTS 播放已打断并清空队列")
    }

    fun markTtsStart(
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ) {
        if (playbackRunning) {
            stopPlayback(onLog, onStatusChanged)
        }
        onStatusChanged("等待 TTS 音频")
        onLog("TTS start：已准备接收并播放下行音频")
    }

    fun markTtsStop(
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ) {
        if (playbackRunning) {
            onStatusChanged("等待播放队列自然结束")
            onLog("TTS stop：服务端结束下发，等待音频队列自然清空")
        } else {
            onStatusChanged("TTS 已结束")
            onLog("TTS stop：当前没有待播放音频")
        }
    }

    fun release() {
        stopRecording()
        recordingJob?.cancel()
        recordingJob = null
        stopPlayback(
            onLog = {},
            onStatusChanged = {},
        )
    }

    private fun startPlaybackIfNeeded(
        onLog: (String) -> Unit,
        onStatusChanged: (String) -> Unit,
    ): PlaybackQueue {
        val currentQueue = playbackQueue
        if (playbackRunning && currentQueue != null) return currentQueue

        val generation = playbackGeneration + 1
        playbackGeneration = generation
        val queue = PlaybackQueue()
        playbackQueue = queue
        playbackRunning = true
        onStatusChanged("播放启动中")

        playbackJob = appScope.launch(Dispatchers.IO) {
            var decoder: OpusDecoder? = null
            var player: AudioPlayer? = null
            var opusFrames = 0
            var pcmBytes = 0L
            try {
                decoder = OpusDecoder()
                player = AudioPlayer()
                player.play()
                onStatusChanged("播放中")
                onLog(
                    "TTS 播放启动：Opus -> PCM16，" +
                        "${AudioConstants.OUTPUT_SAMPLE_RATE}Hz/mono AudioTrack",
                )

                while (playbackRunning && generation == playbackGeneration) {
                    val opus = queue.receiveOrNull(PLAYBACK_IDLE_TIMEOUT_MS) ?: break
                    val pcmFrames = decoder.decode(opus)
                    opusFrames += 1
                    for (pcm in pcmFrames) {
                        if (pcm.isEmpty()) continue
                        player.writePcm(pcm)
                        pcmBytes += pcm.size
                    }

                    if (opusFrames > 0 && opusFrames % DOWNLINK_LOG_INTERVAL_PACKETS == 0) {
                        onStatusChanged("播放中，已解码 ${opusFrames} 帧")
                        onLog("TTS 已解码播放 ${opusFrames} 个 Opus 帧")
                    }
                }
            } catch (exception: Exception) {
                if (generation == playbackGeneration) {
                    onStatusChanged("播放失败")
                    onLog("TTS 播放异常：${exception.message ?: exception::class.java.simpleName}")
                }
            } finally {
                runCatching { player?.release() }
                runCatching { decoder?.release() }
                if (generation == playbackGeneration) {
                    playbackRunning = false
                    playbackQueue?.clear()
                    playbackQueue = null
                    playbackJob = null
                    onStatusChanged("已停止，播放 ${opusFrames} 帧")
                    if (opusFrames > 0 || pcmBytes > 0) {
                        onLog("TTS 播放停止：Opus ${opusFrames} 帧，PCM ${pcmBytes}B")
                    }
                }
            }
        }
        return queue
    }

    private companion object {
        const val UPLINK_LOG_INTERVAL_PACKETS = 50
        const val DOWNLINK_LOG_INTERVAL_PACKETS = 50
        const val PLAYBACK_IDLE_TIMEOUT_MS = 900L
    }
}
