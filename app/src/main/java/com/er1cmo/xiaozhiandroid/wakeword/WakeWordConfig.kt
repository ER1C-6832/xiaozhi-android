package com.er1cmo.xiaozhiandroid.wakeword

data class WakeWordConfig(
    val keyword: String = DEFAULT_KEYWORD,
    val sampleRate: Int = 16_000,
    val frameMs: Int = 100,
    val cooldownMs: Long = 1_500L,
    val callbackDelayAfterHitMs: Long = 300L,
    val keywordsScore: Float = 1.6f,
    val keywordsThreshold: Float = 0.25f,
    val numTrailingBlanks: Int = 1,
) {
    val samplesPerFrame: Int = sampleRate * frameMs / 1_000

    companion object {
        const val DEFAULT_KEYWORD = "小智"
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        const val KEYWORDS_FILE = "$MODEL_DIR/keywords_xiaozhi.txt"
    }
}
