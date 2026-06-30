package com.er1cmo.xiaozhiandroid

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Small trampoline activity used by Android MCP tools to request runtime
 * permissions from a non-Activity execution path.
 */
class PermissionRequestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionOrFinish()
    }

    private fun requestPermissionOrFinish() {
        val permission = intent.getStringExtra(EXTRA_PERMISSION).orEmpty()
        if (permission.isBlank()) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        requestPermissions(arrayOf(permission), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    companion object {
        private const val EXTRA_PERMISSION = "extra_permission"
        private const val REQUEST_CODE = 6808

        fun createIntent(
            context: Context,
            permission: String,
        ): Intent {
            return Intent(context, PermissionRequestActivity::class.java)
                .putExtra(EXTRA_PERMISSION, permission)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
