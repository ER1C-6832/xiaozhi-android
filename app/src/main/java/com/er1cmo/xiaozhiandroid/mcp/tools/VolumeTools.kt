package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.media.AudioManager
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class VolumeGetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_volume"
    override val description: String = "获取媒体、铃声、通知、闹钟、系统等音量与响铃模式。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = JSONArray()
        STREAMS.forEach { stream ->
            val current = audioManager.getStreamVolume(stream.id)
            val max = audioManager.getStreamMaxVolume(stream.id).coerceAtLeast(1)
            val min = runCatching { audioManager.getStreamMinVolume(stream.id) }.getOrDefault(0)
            streams.put(
                JSONObject()
                    .put("stream", stream.name)
                    .put("current", current)
                    .put("min", min)
                    .put("max", max)
                    .put("percent", (current * 100.0 / max).roundToInt()),
            )
        }
        return jsonTextResult(
            JSONObject()
                .put("ringerMode", ringerModeName(audioManager.ringerMode))
                .put("streams", streams),
        )
    }
}

class VolumeSetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_volume"
    override val description: String = "设置指定音频流音量。优先使用 percent，范围 0-100。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put(
                "stream",
                stringProperty(
                    description = "音频流：music 媒体音量，ring 铃声，notification 通知，alarm 闹钟，system 系统。默认 music。",
                    enumValues = STREAMS.map { it.name },
                ),
            )
            .put("percent", numberProperty("目标音量百分比，0 到 100。"))
            .put("index", integerProperty("可选：直接设置系统音量 index。若提供 index，则优先于 percent。")),
        required = JSONArray(),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = streamByName(arguments.optString("stream", "music"))
            ?: return errorText("unknown stream: ${arguments.optString("stream")}")
        val max = audioManager.getStreamMaxVolume(stream.id).coerceAtLeast(1)
        val min = runCatching { audioManager.getStreamMinVolume(stream.id) }.getOrDefault(0)
        val targetIndex = if (arguments.has("index")) {
            arguments.optInt("index", audioManager.getStreamVolume(stream.id))
        } else {
            val percent = arguments.optDouble("percent", 50.0).coerceIn(0.0, 100.0)
            (max * percent / 100.0).roundToInt()
        }.coerceIn(min, max)

        return try {
            audioManager.setStreamVolume(stream.id, targetIndex, AudioManager.FLAG_SHOW_UI)
            val current = audioManager.getStreamVolume(stream.id)
            jsonTextResult(
                JSONObject()
                    .put("stream", stream.name)
                    .put("current", current)
                    .put("min", min)
                    .put("max", max)
                    .put("percent", (current * 100.0 / max).roundToInt()),
            )
        } catch (exception: SecurityException) {
            errorText("设置音量失败：${exception.message ?: "缺少权限或当前勿扰模式限制"}")
        }
    }
}

class RingerModeGetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_ringer_mode"
    override val description: String = "获取当前响铃模式：normal、vibrate、silent。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return jsonTextResult(JSONObject().put("ringerMode", ringerModeName(audioManager.ringerMode)))
    }
}

class RingerModeSetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_ringer_mode"
    override val description: String = "设置响铃模式。部分系统在勿扰模式下需要通知策略权限。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put(
                "mode",
                stringProperty(
                    description = "响铃模式：normal 正常响铃，vibrate 震动，silent 静音。",
                    enumValues = listOf("normal", "vibrate", "silent"),
                ),
            ),
        required = JSONArray().put("mode"),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = when (arguments.optString("mode", "")) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return errorText("unknown mode: ${arguments.optString("mode")}")
        }
        return try {
            audioManager.ringerMode = mode
            jsonTextResult(JSONObject().put("ringerMode", ringerModeName(audioManager.ringerMode)))
        } catch (exception: SecurityException) {
            errorText("设置响铃模式失败：${exception.message ?: "缺少通知策略权限"}")
        }
    }
}

private data class StreamDef(val name: String, val id: Int)

private val STREAMS = listOf(
    StreamDef("music", AudioManager.STREAM_MUSIC),
    StreamDef("ring", AudioManager.STREAM_RING),
    StreamDef("notification", AudioManager.STREAM_NOTIFICATION),
    StreamDef("alarm", AudioManager.STREAM_ALARM),
    StreamDef("system", AudioManager.STREAM_SYSTEM),
)

private fun streamByName(name: String): StreamDef? = STREAMS.firstOrNull { it.name == name }

private fun ringerModeName(mode: Int): String {
    return when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        AudioManager.RINGER_MODE_SILENT -> "silent"
        else -> "unknown"
    }
}
