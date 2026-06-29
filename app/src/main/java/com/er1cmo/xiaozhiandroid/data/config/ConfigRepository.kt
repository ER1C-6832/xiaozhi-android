package com.er1cmo.xiaozhiandroid.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.xiaozhiConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xiaozhi_config",
)

class ConfigRepository(context: Context) {
    private val appContext = context.applicationContext

    val configFlow: Flow<AppConfig> = appContext.xiaozhiConfigDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppConfig(
                clientId = preferences[ConfigKeys.CLIENT_ID].orEmpty(),
                deviceId = preferences[ConfigKeys.DEVICE_ID].orEmpty(),
                serialNumber = preferences[ConfigKeys.SERIAL_NUMBER].orEmpty(),
                hmacKey = preferences[ConfigKeys.HMAC_KEY].orEmpty(),
                activationStatus = preferences[ConfigKeys.ACTIVATION_STATUS] ?: false,
                activationCode = preferences[ConfigKeys.ACTIVATION_CODE].orEmpty(),
                activationChallenge = preferences[ConfigKeys.ACTIVATION_CHALLENGE].orEmpty(),
                activationMessage = preferences[ConfigKeys.ACTIVATION_MESSAGE].orEmpty(),
                otaUrl = preferences[ConfigKeys.OTA_URL] ?: AppConfig.DEFAULT_OTA_URL,
                authorizationUrl = preferences[ConfigKeys.AUTHORIZATION_URL]
                    ?: AppConfig.DEFAULT_AUTHORIZATION_URL,
                websocketUrl = preferences[ConfigKeys.WEBSOCKET_URL].orEmpty(),
                websocketToken = preferences[ConfigKeys.WEBSOCKET_TOKEN].orEmpty(),
                activationVersion = preferences[ConfigKeys.ACTIVATION_VERSION]
                    ?: AppConfig.DEFAULT_ACTIVATION_VERSION,
                lastJson = preferences[ConfigKeys.LAST_JSON].orEmpty(),
            )
        }

    suspend fun getConfig(): AppConfig = configFlow.first()

    suspend fun ensureDefaultConfig() {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            if (preferences[ConfigKeys.OTA_URL].isNullOrBlank()) {
                preferences[ConfigKeys.OTA_URL] = AppConfig.DEFAULT_OTA_URL
            }
            if (preferences[ConfigKeys.AUTHORIZATION_URL].isNullOrBlank()) {
                preferences[ConfigKeys.AUTHORIZATION_URL] = AppConfig.DEFAULT_AUTHORIZATION_URL
            }
            if (preferences[ConfigKeys.ACTIVATION_VERSION].isNullOrBlank()) {
                preferences[ConfigKeys.ACTIVATION_VERSION] = AppConfig.DEFAULT_ACTIVATION_VERSION
            }
        }
    }

    suspend fun saveIdentity(identity: DeviceIdentity) {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences[ConfigKeys.CLIENT_ID] = identity.clientId
            preferences[ConfigKeys.DEVICE_ID] = identity.deviceId
            preferences[ConfigKeys.SERIAL_NUMBER] = identity.serialNumber
            preferences[ConfigKeys.HMAC_KEY] = identity.hmacKey
        }
    }

    suspend fun setActivationStatus(activated: Boolean) {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences[ConfigKeys.ACTIVATION_STATUS] = activated
        }
    }

    suspend fun saveActivationData(
        code: String,
        challenge: String,
        message: String,
    ) {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences[ConfigKeys.ACTIVATION_CODE] = code
            preferences[ConfigKeys.ACTIVATION_CHALLENGE] = challenge
            preferences[ConfigKeys.ACTIVATION_MESSAGE] = message
        }
    }

    suspend fun clearActivationData() {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences.remove(ConfigKeys.ACTIVATION_CODE)
            preferences.remove(ConfigKeys.ACTIVATION_CHALLENGE)
            preferences.remove(ConfigKeys.ACTIVATION_MESSAGE)
        }
    }

    suspend fun saveWebSocketConfig(url: String, token: String) {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences[ConfigKeys.WEBSOCKET_URL] = url
            preferences[ConfigKeys.WEBSOCKET_TOKEN] = token
        }
    }

    suspend fun saveLastJson(value: String) {
        appContext.xiaozhiConfigDataStore.edit { preferences ->
            preferences[ConfigKeys.LAST_JSON] = value
        }
    }
}
