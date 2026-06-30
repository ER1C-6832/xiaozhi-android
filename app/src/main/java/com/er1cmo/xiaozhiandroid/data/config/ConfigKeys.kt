package com.er1cmo.xiaozhiandroid.data.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object ConfigKeys {
    val CLIENT_ID = stringPreferencesKey("client_id")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val SERIAL_NUMBER = stringPreferencesKey("serial_number")
    val HMAC_KEY = stringPreferencesKey("hmac_key")
    val ACTIVATION_STATUS = booleanPreferencesKey("activation_status")
    val ACTIVATION_CODE = stringPreferencesKey("activation_code")
    val ACTIVATION_CHALLENGE = stringPreferencesKey("activation_challenge")
    val ACTIVATION_MESSAGE = stringPreferencesKey("activation_message")
    val OTA_URL = stringPreferencesKey("ota_url")
    val AUTHORIZATION_URL = stringPreferencesKey("authorization_url")
    val WEBSOCKET_URL = stringPreferencesKey("websocket_url")
    val WEBSOCKET_TOKEN = stringPreferencesKey("websocket_token")
    val ACTIVATION_VERSION = stringPreferencesKey("activation_version")
    val LAST_JSON = stringPreferencesKey("last_json")
    val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
}
