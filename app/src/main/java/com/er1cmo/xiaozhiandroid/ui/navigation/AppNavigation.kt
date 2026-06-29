package com.er1cmo.xiaozhiandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
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
    val appScope = rememberCoroutineScope()
    val configRepository = remember { ConfigRepository(appContext) }
    val deviceIdentityManager = remember { DeviceIdentityManager(configRepository) }
    val otaActivationClient = remember { OtaActivationClient(configRepository) }
    val xiaozhiWebSocketClient = remember {
        XiaozhiWebSocketClient(
            configRepository = configRepository,
            appScope = appScope,
        )
    }
    val viewModel = remember {
        MainViewModel(
            configRepository = configRepository,
            deviceIdentityManager = deviceIdentityManager,
            otaActivationClient = otaActivationClient,
            xiaozhiWebSocketClient = xiaozhiWebSocketClient,
            appScope = appScope,
        )
    }
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
