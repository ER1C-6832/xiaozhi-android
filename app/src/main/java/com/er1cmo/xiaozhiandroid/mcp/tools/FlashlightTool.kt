package com.er1cmo.xiaozhiandroid.mcp.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

class FlashlightSetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_flashlight"
    override val description: String = "打开或关闭手机手电筒。需要相机权限和设备支持闪光灯。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("enabled", booleanProperty("true 打开手电筒，false 关闭手电筒。")),
        required = JSONArray().put("enabled"),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            return errorText("缺少 CAMERA 权限，无法控制手电筒。请先在系统权限中允许相机权限。")
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = findFlashCameraId(cameraManager) ?: return errorText("当前设备未找到可用闪光灯")
        val enabled = arguments.optBoolean("enabled", true)

        return try {
            cameraManager.setTorchMode(cameraId, enabled)
            jsonTextResult(
                JSONObject()
                    .put("enabled", enabled)
                    .put("cameraId", cameraId),
            )
        } catch (exception: Exception) {
            errorText("设置手电筒失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }

    private fun findFlashCameraId(cameraManager: CameraManager): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
}
