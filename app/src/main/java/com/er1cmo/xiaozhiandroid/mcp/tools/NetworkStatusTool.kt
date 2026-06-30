package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONArray
import org.json.JSONObject

class NetworkStatusTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_network_status"
    override val description: String = "获取当前网络连接状态、网络类型、是否可联网、是否计费网络。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        if (activeNetwork == null || capabilities == null) {
            return jsonTextResult(
                JSONObject()
                    .put("connected", false)
                    .put("transports", JSONArray())
                    .put("hasInternetCapability", false)
                    .put("validated", false),
            )
        }

        return jsonTextResult(
            JSONObject()
                .put("connected", true)
                .put("transports", JSONArray(transports(capabilities)))
                .put("hasInternetCapability", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .put("validated", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .put("notMetered", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                .put("vpn", capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)),
        )
    }

    private fun transports(capabilities: NetworkCapabilities): List<String> {
        val transports = mutableListOf<String>()
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("wifi")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("bluetooth")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("vpn")
        return transports
    }
}
