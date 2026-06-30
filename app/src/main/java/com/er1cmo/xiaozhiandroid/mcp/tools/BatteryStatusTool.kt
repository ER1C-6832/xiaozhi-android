package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONObject

class BatteryStatusTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_battery_status"
    override val description: String = "获取手机电量、充电状态、温度、电压等电池信息。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return errorText("无法读取电池状态")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        return jsonTextResult(
            JSONObject()
                .put("level", level)
                .put("scale", scale)
                .put("percent", percent)
                .put("status", batteryStatusName(status))
                .put("isCharging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                .put("plugged", pluggedName(plugged))
                .put("temperatureCelsius", if (temperatureTenths >= 0) temperatureTenths / 10.0 else JSONObject.NULL)
                .put("voltageMv", if (voltageMv >= 0) voltageMv else JSONObject.NULL),
        )
    }

    private fun batteryStatusName(status: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }

    private fun pluggedName(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> "none"
            else -> "other"
        }
    }
}
