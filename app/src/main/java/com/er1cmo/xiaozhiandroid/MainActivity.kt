package com.er1cmo.xiaozhiandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.er1cmo.xiaozhiandroid.ui.navigation.AppNavigation
import com.er1cmo.xiaozhiandroid.ui.theme.XiaozhiAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaozhiAndroidTheme {
                AppNavigation()
            }
        }
    }
}
