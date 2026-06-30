package com.er1cmo.xiaozhiandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.er1cmo.xiaozhiandroid.core.AppController
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
    val appController = remember {
        AppController.create(
            context = appContext,
            appScope = appScope,
        )
    }
    val viewModel = remember(appController) {
        MainViewModel(
            configRepository = appController.configRepository,
            deviceIdentityManager = appController.deviceIdentityManager,
            otaActivationClient = appController.otaActivationClient,
            xiaozhiWebSocketClient = appController.xiaozhiWebSocketClient,
            audioEngine = appController.audioEngine,
            appScope = appController.appScope,
        )
    }
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }

    LaunchedEffect(appController) {
        appController.start()
    }

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
            onSaveSystemSettings = viewModel::saveSystemSettings,
            onReactivate = viewModel::reactivate,
            onResetNetwork = viewModel::resetNetworkConfig,
            onResetIdentity = viewModel::resetDeviceIdentity,
            onClearLogs = viewModel::clearDebugLogs,
        )
    }
}
