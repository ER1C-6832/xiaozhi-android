package com.er1cmo.xiaozhiandroid.audio

object AudioConstants {
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val FRAME_DURATION_MS = 20
    const val SAMPLES_PER_FRAME = INPUT_SAMPLE_RATE * FRAME_DURATION_MS / 1_000
    const val OUTPUT_SAMPLES_PER_FRAME = OUTPUT_SAMPLE_RATE * FRAME_DURATION_MS / 1_000
    const val BYTES_PER_SAMPLE = 2
    const val PCM_FRAME_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE
    const val OUTPUT_PCM_FRAME_BYTES = OUTPUT_SAMPLES_PER_FRAME * BYTES_PER_SAMPLE
    const val OPUS_BITRATE = 24_000
    const val OPUS_MIME_TYPE = "audio/opus"
}
