package com.zhubby.klawchat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gatewaySettingsDataStore by preferencesDataStore("gateway_settings")
private const val DEFAULT_GATEWAY_BASE_URL = "http://10.0.2.2:3000"

data class GatewaySettings(
    val baseUrl: String = DEFAULT_GATEWAY_BASE_URL,
    val token: String = "",
    val streamEnabled: Boolean = true,
    val lastSessionKey: String? = null,
)

class GatewaySettingsStore(private val context: Context) {
    val settings: Flow<GatewaySettings> = context.gatewaySettingsDataStore.data.map { preferences ->
        GatewaySettings(
            baseUrl = preferences[BASE_URL] ?: DEFAULT_GATEWAY_BASE_URL,
            token = preferences[TOKEN].orEmpty(),
            streamEnabled = preferences[STREAM_ENABLED] ?: true,
            lastSessionKey = preferences[LAST_SESSION_KEY],
        )
    }

    suspend fun save(settings: GatewaySettings) {
        context.gatewaySettingsDataStore.edit { preferences ->
            preferences[BASE_URL] = settings.baseUrl
            preferences[TOKEN] = settings.token
            preferences[STREAM_ENABLED] = settings.streamEnabled
            settings.lastSessionKey?.let { preferences[LAST_SESSION_KEY] = it }
                ?: preferences.remove(LAST_SESSION_KEY)
        }
    }

    suspend fun saveLastSession(sessionKey: String?) {
        context.gatewaySettingsDataStore.edit { preferences ->
            sessionKey?.let { preferences[LAST_SESSION_KEY] = it }
                ?: preferences.remove(LAST_SESSION_KEY)
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val STREAM_ENABLED = booleanPreferencesKey("stream_enabled")
        val LAST_SESSION_KEY = stringPreferencesKey("last_session_key")
    }
}
