package com.er1cmo.xiaozhiandroid.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Android platform Opus encoder wrapper.
 *
 * The xiaozhi WebSocket protocol expects binary frames to be Opus packets.
 * py-xiaozhi declares the same protocol audio parameters in hello:
 * 16 kHz, mono, 20 ms frames. This class keeps the Android implementation
 * aligned with those protocol parameters while hiding MediaCodec details.
 */
class OpusEncoder {
    private val codec: MediaCodec = try {
        MediaCodec.createEncoderByType(AudioConstants.OPUS_MIME_TYPE)
    } catch (exception: Exception) {
        throw OpusEncodingException(
            "当前设备没有可用的系统 Opus 编码器，无法上传语音。后续可替换为 libopus JNI。",
            exception,
        )
    }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var released = false

    init {
        try {
            val format = MediaFormat.createAudioFormat(
                AudioConstants.OPUS_MIME_TYPE,
                AudioConstants.INPUT_SAMPLE_RATE,
                AudioConstants.CHANNELS,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AudioConstants.OPUS_BITRATE)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioConstants.PCM_FRAME_BYTES)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (exception: Exception) {
            runCatching { codec.release() }
            throw OpusEncodingException("初始化 Opus 编码器失败：${exception.message}", exception)
        }
    }

    fun encode(frame: PcmFrame): List<ByteArray> {
        ensureNotReleased()
        queueInput(frame)
        return drainOutput(endOfStream = false)
    }

    fun release() {
        if (released) return
        released = true
        try {
            queueEndOfStream()
            drainOutput(endOfStream = true)
        } catch (_: Exception) {
            // Ignore flush errors during release.
        }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun queueInput(frame: PcmFrame) {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex < 0) {
            throw OpusEncodingException("Opus 编码器暂时没有可用输入缓冲区")
        }

        val inputBuffer = codec.getInputBuffer(inputIndex)
            ?: throw OpusEncodingException("Opus 编码器输入缓冲区为空")
        inputBuffer.clear()
        if (frame.bytes.size > inputBuffer.capacity()) {
            throw OpusEncodingException("PCM 帧过大：${frame.bytes.size}B > ${inputBuffer.capacity()}B")
        }
        inputBuffer.put(frame.bytes)
        codec.queueInputBuffer(inputIndex, 0, frame.bytes.size, frame.presentationTimeUs, 0)
    }

    private fun queueEndOfStream() {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    private fun drainOutput(endOfStream: Boolean): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(
                bufferInfo,
                if (endOfStream) OUTPUT_TIMEOUT_US else 0L,
            )
            when {
                outputIndex >= 0 -> {
                    val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex)
                        ?: throw OpusEncodingException("Opus 编码器输出缓冲区为空")
                    if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val packet = ByteArray(bufferInfo.size)
                        outputBuffer.get(packet)
                        packets += packet
                    }
                    val reachedEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changes are expected for some encoders; raw packets are handled later.
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
            }
        }
        return packets
    }

    private fun ensureNotReleased() {
        if (released) throw OpusEncodingException("Opus 编码器已释放")
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L
    }
}
