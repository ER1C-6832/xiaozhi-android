package com.er1cmo.xiaozhiandroid.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Android platform Opus decoder wrapper for server TTS binary frames.
 *
 * The xiaozhi WebSocket protocol sends TTS audio as binary Opus frames. This
 * class decodes one raw Opus packet at a time into PCM16 and keeps playback
 * details out of the WebSocket layer.
 */
class OpusDecoder {
    private val codec: MediaCodec = try {
        MediaCodec.createDecoderByType(AudioConstants.OPUS_MIME_TYPE)
    } catch (exception: Exception) {
        throw OpusEncodingException(
            "当前设备没有可用的系统 Opus 解码器，无法播放 TTS。后续可替换为 libopus JNI。",
            exception,
        )
    }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var released = false
    private var frameIndex = 0L

    init {
        try {
            val format = MediaFormat.createAudioFormat(
                AudioConstants.OPUS_MIME_TYPE,
                AudioConstants.OUTPUT_SAMPLE_RATE,
                AudioConstants.CHANNELS,
            ).apply {
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_OPUS_INPUT_SIZE)
            }
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (exception: Exception) {
            runCatching { codec.release() }
            throw OpusEncodingException("初始化 Opus 解码器失败：${exception.message}", exception)
        }
    }

    fun decode(packet: ByteArray): List<ByteArray> {
        ensureNotReleased()
        queueInput(packet)
        return drainOutput(endOfStream = false)
    }

    fun release() {
        if (released) return
        released = true
        try {
            queueEndOfStream()
            drainOutput(endOfStream = true)
        } catch (_: Exception) {
            // Ignore release-time drain errors.
        }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun queueInput(packet: ByteArray) {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex < 0) {
            throw OpusEncodingException("Opus 解码器暂时没有可用输入缓冲区")
        }
        val inputBuffer = codec.getInputBuffer(inputIndex)
            ?: throw OpusEncodingException("Opus 解码器输入缓冲区为空")
        inputBuffer.clear()
        if (packet.size > inputBuffer.capacity()) {
            throw OpusEncodingException("Opus 包过大：${packet.size}B > ${inputBuffer.capacity()}B")
        }
        inputBuffer.put(packet)
        val presentationTimeUs = frameIndex * AudioConstants.FRAME_DURATION_MS * 1_000L
        frameIndex += 1
        codec.queueInputBuffer(inputIndex, 0, packet.size, presentationTimeUs, 0)
    }

    private fun queueEndOfStream() {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    private fun drainOutput(endOfStream: Boolean): List<ByteArray> {
        val pcmFrames = mutableListOf<ByteArray>()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(
                bufferInfo,
                if (endOfStream) OUTPUT_TIMEOUT_US else 0L,
            )
            when {
                outputIndex >= 0 -> {
                    val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex)
                        ?: throw OpusEncodingException("Opus 解码器输出缓冲区为空")
                    if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val pcm = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcm)
                        pcmFrames += pcm
                    }
                    val reachedEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Keep the player fixed at protocol default 24 kHz mono for now.
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
            }
        }
        return pcmFrames
    }

    private fun ensureNotReleased() {
        if (released) throw OpusEncodingException("Opus 解码器已释放")
    }

    private companion object {
        const val MAX_OPUS_INPUT_SIZE = 4_096
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L
    }
}
