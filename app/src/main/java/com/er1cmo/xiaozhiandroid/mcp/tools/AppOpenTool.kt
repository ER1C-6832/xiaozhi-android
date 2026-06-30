package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AppOpenTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.open_app"
    override val description: String = "打开手机上的 App。可传 packageName，也可传 appName，例如 Chrome、Gmail、Play Store、微信。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("packageName", stringProperty("需要打开的 Android 应用包名，例如 com.android.chrome。可选。"))
            .put("appName", stringProperty("应用名称或关键词，例如 Chrome、Gmail、Play Store、微信。可选。")),
        required = JSONArray(),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val packageName = arguments.optString("packageName", "").trim()
        val appName = arguments.optString("appName", "").trim()
            .ifBlank { arguments.optString("name", "").trim() }
            .ifBlank { arguments.optString("query", "").trim() }
            .ifBlank { arguments.optString("keyword", "").trim() }

        if (packageName.isBlank() && appName.isBlank()) {
            return errorText("必须提供 packageName 或 appName。示例：packageName=com.android.chrome，或 appName=Chrome。")
        }

        val candidates = buildPackageCandidates(packageName, appName)
        val tried = mutableListOf<String>()

        for (candidate in candidates) {
            tried += candidate
            val launchIntent = findLaunchIntentByPackage(candidate)
            if (launchIntent != null) {
                return launch(candidate, appName, launchIntent, tried)
            }
        }

        if (appName.isNotBlank()) {
            val byLabel = findLaunchIntentByLabel(appName)
            if (byLabel != null) {
                val (resolvedPackage, launchIntent) = byLabel
                return launch(resolvedPackage, appName, launchIntent, tried)
            }
        }

        return errorText(
            buildString {
                append("未找到可启动应用。")
                if (packageName.isNotBlank()) append(" packageName=$packageName。")
                if (appName.isNotBlank()) append(" appName=$appName。")
                if (tried.isNotEmpty()) append(" 已尝试包名：${tried.joinToString()}。")
                append(" 如果这是 Android 11+ 包可见性问题，请确认 Manifest queries 已覆盖该应用，或传入精确包名。")
            },
        )
    }

    private fun launch(
        packageName: String,
        appName: String,
        launchIntent: Intent,
        tried: List<String>,
    ): McpToolCallResult {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            jsonTextResult(
                JSONObject()
                    .put("opened", true)
                    .put("packageName", packageName)
                    .put("appName", appName.ifBlank { JSONObject.NULL })
                    .put("triedPackages", JSONArray(tried)),
            )
        } catch (exception: Exception) {
            errorText("打开应用失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }

    private fun buildPackageCandidates(
        packageName: String,
        appName: String,
    ): List<String> {
        val candidates = linkedSetOf<String>()
        if (packageName.isNotBlank()) {
            candidates += packageName
            packageAliases[normalize(packageName)]?.let { candidates.addAll(it) }
        }
        if (appName.isNotBlank()) {
            packageAliases[normalize(appName)]?.let { candidates.addAll(it) }
        }
        return candidates.toList()
    }

    private fun findLaunchIntentByPackage(packageName: String): Intent? {
        val manager = context.packageManager
        manager.getLaunchIntentForPackage(packageName)?.let { return it }

        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val activities = manager.queryIntentActivitiesCompat(launcherIntent)
        val activity = activities.firstOrNull() ?: return null
        val component = ComponentName(
            activity.activityInfo.packageName,
            activity.activityInfo.name,
        )
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
    }

    private fun findLaunchIntentByLabel(appName: String): Pair<String, Intent>? {
        val normalizedQuery = normalize(appName)
        if (normalizedQuery.isBlank()) return null

        val manager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = manager.queryIntentActivitiesCompat(launcherIntent)

        val exact = activities.firstOrNull { info ->
            normalize(info.loadLabel(manager).toString()) == normalizedQuery ||
                normalize(info.activityInfo.packageName) == normalizedQuery
        }
        val fuzzy = exact ?: activities.firstOrNull { info ->
            val label = normalize(info.loadLabel(manager).toString())
            val pkg = normalize(info.activityInfo.packageName)
            label.contains(normalizedQuery) || normalizedQuery.contains(label) || pkg.contains(normalizedQuery)
        } ?: return null

        val packageName = fuzzy.activityInfo.packageName
        val component = ComponentName(packageName, fuzzy.activityInfo.name)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
        return packageName to intent
    }

    private fun PackageManager.queryIntentActivitiesCompat(intent: Intent) = queryIntentActivities(intent, 0)

    private fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
    }

    companion object {
        private val packageAliases = mapOf(
            "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome", "com.chrome.beta", "com.chrome.dev"),
            "googlechrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
            "浏览器" to listOf("com.android.chrome", "com.google.android.apps.chrome", "com.android.browser"),
            "gmail" to listOf("com.google.android.gm"),
            "googlemail" to listOf("com.google.android.gm"),
            "邮件" to listOf("com.google.android.gm"),
            "playstore" to listOf("com.android.vending"),
            "googleplay" to listOf("com.android.vending"),
            "应用商店" to listOf("com.android.vending"),
            "youtube" to listOf("com.google.android.youtube"),
            "maps" to listOf("com.google.android.apps.maps"),
            "googlemaps" to listOf("com.google.android.apps.maps"),
            "地图" to listOf("com.google.android.apps.maps"),
            "settings" to listOf("com.android.settings"),
            "设置" to listOf("com.android.settings"),
            "wechat" to listOf("com.tencent.mm"),
            "weixin" to listOf("com.tencent.mm"),
            "微信" to listOf("com.tencent.mm"),
            "qq" to listOf("com.tencent.mobileqq"),
            "支付宝" to listOf("com.eg.android.AlipayGphone"),
            "alipay" to listOf("com.eg.android.AlipayGphone"),
            "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "whatsapp" to listOf("com.whatsapp"),
            "spotify" to listOf("com.spotify.music"),
        )
    }
}
