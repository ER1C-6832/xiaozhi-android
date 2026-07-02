package com.er1cmo.xiaozhiandroid.wakeword

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKwsKeywordsFile
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import java.util.Locale

class SherpaWakeWordDetector(
    private val context: Context,
    private val config: WakeWordConfig,
) {
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    fun initialize() {
        validateAssets(context.assets)
        val kwsConfig = KeywordSpotterConfig(
            featConfig = getFeatureConfig(sampleRate = config.sampleRate, featureDim = 80),
            modelConfig = getKwsModelConfig(),
            keywordsFile = getKwsKeywordsFile(),
            keywordsScore = config.keywordsScore,
            keywordsThreshold = config.keywordsThreshold,
            numTrailingBlanks = config.numTrailingBlanks,
        )
        spotter = KeywordSpotter(
            assetManager = context.assets,
            config = kwsConfig,
        )
        resetStream()
    }

    fun accept(samples: FloatArray): String? {
        val kws = spotter ?: return null
        val activeStream = stream ?: return null
        activeStream.acceptWaveform(samples = samples, sampleRate = config.sampleRate)
        var detected: String? = null
        while (kws.isReady(activeStream)) {
            kws.decode(activeStream)
            val result = kws.getResult(activeStream).keyword
            if (result.isNotBlank()) {
                detected = result
                resetStream()
                break
            }
        }
        return detected
    }

    fun resetStream() {
        runCatching { stream?.release() }
        // The 2025 zh-en model uses a phone+ppinyin keyword grammar. We keep the
        // generated keywords_xiaozhi.txt as the primary keyword list and also pass
        // the active keyword grammar here so future custom keywords can be tested
        // without regenerating the asset file.
        val customKeywords = keywordGrammar(config.keyword)
        stream = spotter?.createStream(customKeywords)
    }

    fun release() {
        runCatching { stream?.release() }
        runCatching { spotter?.release() }
        stream = null
        spotter = null
    }

    private fun validateAssets(assetManager: AssetManager) {
        val dir = WakeWordConfig.MODEL_DIR
        val names = runCatching { assetManager.list(dir)?.toSet().orEmpty() }.getOrElse { emptySet() }
        val missing = REQUIRED_MODEL_FILES.filterNot { it in names }
        if (missing.isNotEmpty()) {
            error(
                "Sherpa KWS 2025 模型资产缺失：assets/$dir 缺少 ${missing.joinToString()}。" +
                    "请先运行 tools/prepare_sherpa_kws_assets.ps1，或按 docs/phase12a_kws_2025_delivery_report.md 手动放置模型。",
            )
        }
    }

    private fun keywordGrammar(keyword: String): String {
        val normalized = keyword.trim().ifBlank { WakeWordConfig.DEFAULT_KEYWORD }
        return if (normalized == WakeWordConfig.DEFAULT_KEYWORD) {
            XIAOZHI_KEYWORD_GRAMMAR
        } else {
            normalized
        }
    }

    companion object {
        const val XIAOZHI_KEYWORD_GRAMMAR =
            "x iǎo zh ì @小智/" +
                "x iǎo zh ī @小知/" +
                "x iǎo zh ì t óng x ué @小智同学"

        private val REQUIRED_MODEL_FILES = listOf(
            "encoder-epoch-13-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-13-avg-2-chunk-16-left-64.onnx",
            "joiner-epoch-13-avg-2-chunk-16-left-64.onnx",
            "tokens.txt",
            "keywords_xiaozhi.txt",
        )
    }
}

fun normalizeWakeWordHit(value: String): String {
    return value
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{Punct}，。！？、,.!?：:；;\"'`~·_\\-]"), "")
        .replace("xiaozi", "xiaozhi")
        .replace("xiaozhi", "小智")
}
