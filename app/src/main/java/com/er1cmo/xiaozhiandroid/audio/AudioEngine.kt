package com.er1cmo.xiaozhiandroid.audio

import android.Manifest
import android.media.AudioRecord
import androidx.annotation.RequiresPermission
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AudioEngine(
    private val appScope: CoroutineScope,
) {
    data class RecordingStats(
        val pcmFrames: Int = 0,
        val opusPackets: Int = 0,
        val failedSends: Int = 0,
        val peakAbs: Int = 0,
        val rms: Int = 0,
        val silentFrames: Int = 0,
    ) {
        val silentRatioPercent: Int
            get() = if (pcmFrames <= 0) 0 else (silentFrames * 100 / pcmFrames)
    }

    @Volatile
    private var recording = false

    @Volatile
    private var playbackRunning = false

    @Volatile
    private var playbackGeneration = 0

    @Volatile
    private var currentRecordingStats = RecordingStats()

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null
    private var playbackQueue: PlaybackQueue? = null

    fun isRecording(): Boolean = recording

    fun isPlaybackActive(): Boolean = playbackRunning

    fun recordingStats(): RecordingStats = currentRecordingStats

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
        currentRecordingStats = RecordingStats()
        onStatusChanged("录音启动中")
        recordingJob = appScope.launch(Dispatchers.IO) {
            val recorder = AudioRecorder()
            var audioRecord: AudioRecord? = null
            var encoder: OpusEncoder? = null
            var pcmFrames = 0
            var opusPackets = 0
            var failedSends = 0
            var peakAbs = 0
            var silentFrames = 0
            var squareSum = 0.0
            var sampleCount = 0L

            fun updateStats() {
                val rms = if (sampleCount <= 0) {
                    0
                } else {
                    sqrt(squareSum / sampleCount).toInt()
                }
                currentRecordingStats = RecordingStats(
                    pcmFrames = pcmFrames,
                    opusPackets = opusPackets,
                    failedSends = failedSends,
                    peakAbs = peakAbs,
                    rms = rms,
                    silentFrames = silentFrames,
                )
            }

            try {
                if (!recording) {
                    onLog("音频上行启动已取消：尚未打开麦克风")
                    return@launch
                }

                encoder = OpusEncoder()
                audioRecord = recorder.createAudioRecord()
                activeAudioRecord = audioRecord
                if (!recording) {
                    onLog("音频上行启动已取消：AudioRecord 已创建但用户已松开")
                    return@launch
                }

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

                    val frameLevel = inspectPcm16(buffer)
                    peakAbs = maxOf(peakAbs, frameLevel.peakAbs)
                    squareSum += frameLevel.squareSum
                    sampleCount += frameLevel.sampleCount
                    if (frameLevel.peakAbs < SILENCE_PEAK_THRESHOLD) {
                        silentFrames += 1
                    }

                    val pcm = buffer.copyOf()
                    val presentationTimeUs = pcmFrames * AudioConstants.FRAME_DURATION_MS * 1_000L
                    pcmFrames += 1
                    updateStats()

                    val packets = encoder.encode(PcmFrame(pcm, presentationTimeUs))
                    for (packet in packets) {
                        if (packet.isEmpty()) continue
                        val sent = onEncodedFrame(packet)
                        if (!sent) {
                            failedSends += 1
                            updateStats()
                            onLog("Opus 音频帧发送失败，停止录音上行")
                            recording = false
                            break
                        }
                        opusPackets += 1
                        updateStats()
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
                updateStats()
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
                runCatching { encoder?.release() }
                val stats = currentRecordingStats
                onStatusChanged("已停止，累计发送 ${stats.opusPackets} 帧")
                onLog(
                    "音频上行停止：PCM ${stats.pcmFrames} 帧，Opus ${stats.opusPackets} 帧，" +
                        "peak=${stats.peakAbs}, rms=${stats.rms}, 静音 ${stats.silentRatioPercent}%",
                )
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        runCatching { activeAudioRecord?.stop() }
    }

    suspend fun stopRecordingAndAwait(timeoutMs: Long = RECORDING_STOP_JOIN_TIMEOUT_MS): RecordingStats {
        val job = recordingJob
        stopRecording()
        if (job != null) {
            withTimeoutOrNull(timeoutMs) {
                job.join()
            }
        }
        return currentRecordingStats
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
        onStatusChanged("TTS 缓冲中")
        onLog("TTS start：等待预缓冲后播放下行音频")
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
        onStatusChanged("TTS 缓冲中，目标 ${PREBUFFER_TARGET_FRAMES} 帧")

        playbackJob = appScope.launch(Dispatchers.IO) {
            var decoder: OpusDecoder? = null
            var player: AudioPlayer? = null
            var opusFrames = 0
            var pcmBytes = 0L
            var playbackStarted = false
            var idleCount = 0
            val prebufferDeadlineAt = System.currentTimeMillis() + PREBUFFER_MAX_WAIT_MS
            try {
                decoder = OpusDecoder()
                player = AudioPlayer()
                onLog(
                    "TTS 播放准备：Opus -> PCM16，" +
                        "${AudioConstants.OUTPUT_SAMPLE_RATE}Hz/mono AudioTrack，预缓冲 ${PREBUFFER_TARGET_FRAMES} 帧",
                )

                while (playbackRunning && generation == playbackGeneration) {
                    val waitMs = if (playbackStarted) {
                        PLAYBACK_IDLE_TIMEOUT_MS
                    } else {
                        PREBUFFER_FRAME_WAIT_MS
                    }
                    val opus = queue.receiveOrNull(waitMs)
                    if (opus == null) {
                        if (!playbackStarted && System.currentTimeMillis() < prebufferDeadlineAt) {
                            continue
                        }
                        if (playbackStarted) {
                            idleCount += 1
                            if (idleCount <= MAX_IDLE_POLLS_AFTER_PLAYBACK) {
                                continue
                            }
                        }
                        break
                    }
                    idleCount = 0

                    val pcmFrames = decoder.decode(opus)
                    opusFrames += 1
                    for (pcm in pcmFrames) {
                        if (pcm.isEmpty()) continue
                        player.writePcm(pcm)
                        pcmBytes += pcm.size
                    }

                    if (!playbackStarted && shouldStartPlayback(opusFrames, prebufferDeadlineAt)) {
                        player.play()
                        playbackStarted = true
                        onStatusChanged("播放中，已预缓冲 ${opusFrames} 帧")
                        onLog("TTS 预缓冲完成：${opusFrames} 帧，PCM ${pcmBytes}B，开始播放")
                    }

                    if (playbackStarted && opusFrames > 0 && opusFrames % DOWNLINK_LOG_INTERVAL_PACKETS == 0) {
                        onStatusChanged("播放中，已解码 ${opusFrames} 帧，队列 ${queue.depth()} 帧")
                        onLog("TTS 已解码播放 ${opusFrames} 个 Opus 帧，队列 ${queue.depth()} 帧")
                    }
                }

                if (!playbackStarted && opusFrames > 0 && generation == playbackGeneration) {
                    player.play()
                    playbackStarted = true
                    onStatusChanged("播放中，尾帧补播")
                    onLog("TTS 预缓冲不足但已有 ${opusFrames} 帧，执行尾帧补播")
                }
            } catch (exception: Exception) {
                if (generation == playbackGeneration) {
                    onStatusChanged("播放失败")
                    onLog("TTS 播放异常：${exception.message ?: exception::class.java.simpleName}")
                }
            } finally {
                if (playbackStarted && opusFrames > 0) {
                    runCatching { Thread.sleep(PLAYBACK_DRAIN_GRACE_MS) }
                }
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

    private fun shouldStartPlayback(
        opusFrames: Int,
        deadlineAt: Long,
    ): Boolean {
        return opusFrames >= PREBUFFER_TARGET_FRAMES || System.currentTimeMillis() >= deadlineAt
    }

    private fun inspectPcm16(bytes: ByteArray): FrameLevel {
        var peak = 0
        var sum = 0.0
        var samples = 0L
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt()
            var sample = (hi shl 8) or lo
            if (sample > Short.MAX_VALUE) sample -= 0x10000
            val absValue = abs(sample)
            peak = maxOf(peak, absValue)
            sum += sample.toDouble() * sample.toDouble()
            samples += 1
            i += 2
        }
        return FrameLevel(
            peakAbs = peak,
            squareSum = sum,
            sampleCount = samples,
        )
    }

    private data class FrameLevel(
        val peakAbs: Int,
        val squareSum: Double,
        val sampleCount: Long,
    )

    private companion object {
        const val UPLINK_LOG_INTERVAL_PACKETS = 50
        const val DOWNLINK_LOG_INTERVAL_PACKETS = 50
        const val PREBUFFER_TARGET_FRAMES = 10
        const val PREBUFFER_MAX_WAIT_MS = 260L
        const val PREBUFFER_FRAME_WAIT_MS = 20L
        const val PLAYBACK_IDLE_TIMEOUT_MS = 350L
        const val MAX_IDLE_POLLS_AFTER_PLAYBACK = 3
        const val PLAYBACK_DRAIN_GRACE_MS = 180L
        const val RECORDING_STOP_JOIN_TIMEOUT_MS = 1_500L
        const val SILENCE_PEAK_THRESHOLD = 500
    }
}
