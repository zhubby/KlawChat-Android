package com.zhubby.klawchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhubby.klawchat.data.settings.GatewaySettings
import com.zhubby.klawchat.data.settings.GatewaySettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = GatewaySettingsStore(application.applicationContext)
    private val _settings = MutableStateFlow(GatewaySettings())
    val settings: StateFlow<GatewaySettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings -> _settings.value = settings }
        }
    }

    fun updateBaseUrl(baseUrl: String) {
        _settings.update { it.copy(baseUrl = baseUrl) }
    }

    fun updateToken(token: String) {
        _settings.update { it.copy(token = token) }
    }

    fun updateStreamEnabled(enabled: Boolean) {
        _settings.update { it.copy(streamEnabled = enabled) }
    }

    fun save() {
        viewModelScope.launch {
            settingsStore.save(_settings.value)
        }
    }
}
