package com.er1cmo.xiaozhiandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.ui.main.MainScreen
import com.er1cmo.xiaozhiandroid.ui.main.MainViewModel
import com.er1cmo.xiaozhiandroid.ui.settings.SettingsScreen

private enum class AppScreen {
    Main,
    Settings,
}

@Composable
fun AppNavigation() {
    val appContext = LocalContext.current.applicationContext
    val configRepository = remember { ConfigRepository(appContext) }
    val deviceIdentityManager = remember { DeviceIdentityManager(configRepository) }
    val viewModel = remember { MainViewModel(configRepository, deviceIdentityManager) }
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }

    LaunchedEffect(viewModel) {
        viewModel.initialize()
    }

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
