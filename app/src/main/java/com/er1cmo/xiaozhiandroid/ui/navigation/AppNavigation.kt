package com.er1cmo.xiaozhiandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.ui.main.MainScreen
import com.er1cmo.xiaozhiandroid.ui.main.MainViewModel
import com.er1cmo.xiaozhiandroid.ui.settings.SettingsScreen

private enum class AppScreen {
    Main,
    Settings,
}

@Composable
fun AppNavigation() {
    val viewModel = remember { MainViewModel() }
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }

    when (currentScreen) {
        AppScreen.Main -> MainScreen(
            viewModel = viewModel,
            onOpenSettings = {
                viewModel.appendLocalLog("打开参数设置")
                currentScreen = AppScreen.Settings
            },
        )

        AppScreen.Settings -> SettingsScreen(
            uiState = viewModel.uiState,
            onBack = {
                viewModel.appendLocalLog("返回主界面")
                currentScreen = AppScreen.Main
            },
        )
    }
}
