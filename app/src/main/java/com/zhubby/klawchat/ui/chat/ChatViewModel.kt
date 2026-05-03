package com.zhubby.klawchat.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhubby.klawchat.data.gateway.ArchiveApi
import com.zhubby.klawchat.data.gateway.ArchiveAttachment
import com.zhubby.klawchat.data.gateway.ChatRepository
import com.zhubby.klawchat.data.gateway.GatewayConnectionState
import com.zhubby.klawchat.data.gateway.GatewayEndpoint
import com.zhubby.klawchat.data.gateway.GatewayServerFrame
import com.zhubby.klawchat.data.gateway.GatewayWsClient
import com.zhubby.klawchat.data.gateway.chatMessage
import com.zhubby.klawchat.data.gateway.string
import com.zhubby.klawchat.data.settings.GatewaySettings
import com.zhubby.klawchat.data.settings.GatewaySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = GatewaySettingsStore(application.applicationContext)
    private val repository = ChatRepository(GatewayWsClient())
    private val archiveApi = ArchiveApi()
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSettings = GatewaySettings()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                val connectionChanged = settings.baseUrl != currentSettings.baseUrl ||
                    settings.token != currentSettings.token
                currentSettings = settings
                _uiState.update { it.copy(streamEnabled = settings.streamEnabled) }
                if (connectionChanged || uiState.value.connectionStatus == ConnectionStatus.Disconnected) {
                    connect()
                }
            }
        }
        viewModelScope.launch {
            repository.connectionStates.collect { state ->
                _uiState.update {
                    when (state) {
                        GatewayConnectionState.Connected -> it.copy(
                            connectionStatus = ConnectionStatus.Connected,
                            statusMessage = "Connected",
                        )
                        GatewayConnectionState.Connecting -> it.copy(
                            connectionStatus = ConnectionStatus.Connecting,
                            statusMessage = "Connecting",
                        )
                        GatewayConnectionState.Disconnected -> it.copy(
                            connectionStatus = ConnectionStatus.Disconnected,
                            statusMessage = "Disconnected",
                        )
                        is GatewayConnectionState.Failed -> it.copy(
                            connectionStatus = ConnectionStatus.Error,
                            statusMessage = state.message,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.events.collect(::handleEvent)
        }
    }

    fun connect() {
        viewModelScope.launch {
            runCatching {
                repository.connect(GatewayEndpoint(currentSettings.baseUrl, currentSettings.token))
                repository.ping()
                val bootstrap = repository.bootstrap()
                val catalog = repository.listProviders()
                val selected = currentSettings.lastSessionKey
                    ?.takeIf { key -> bootstrap.sessions.any { it.sessionKey == key } }
                    ?: bootstrap.activeSessionKey
                    ?: bootstrap.sessions.firstOrNull()?.sessionKey
                _uiState.update {
                    it.copy(
                        sessions = bootstrap.sessions,
                        selectedSessionKey = selected,
                        providers = catalog.providers,
                        defaultProvider = catalog.defaultProvider,
                        connectionStatus = ConnectionStatus.Connected,
                        statusMessage = "Connected",
                    )
                }
                selected?.let { selectSession(it) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        statusMessage = error.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun createSession() {
        viewModelScope.launch {
            runBusy {
                val session = repository.createSession()
                _uiState.update { state ->
                    state.copy(
                        sessions = listOf(session) + state.sessions,
                        selectedSessionKey = session.sessionKey,
                    )
                }
                selectSession(session.sessionKey)
            }
        }
    }

    fun selectSession(sessionKey: String) {
        viewModelScope.launch {
            settingsStore.saveLastSession(sessionKey)
            _uiState.update { it.copy(selectedSessionKey = sessionKey) }
            runCatching {
                repository.subscribe(sessionKey)
                val history = repository.loadHistory(sessionKey)
                _uiState.update { state ->
                    state.copy(messagesBySession = state.messagesBySession + (sessionKey to history.messages))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message ?: "Failed to load history") }
            }
        }
    }

    fun sendMessage(input: String, attachments: List<ArchiveAttachment> = emptyList()) {
        val trimmed = input.trim()
        val session = uiState.value.selectedSession ?: return
        val selectedAttachments = attachments.ifEmpty { uiState.value.pendingAttachments }
        if (trimmed.isEmpty() && selectedAttachments.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.submit(
                    sessionKey = session.sessionKey,
                    input = trimmed.ifEmpty { "Attachment" },
                    stream = currentSettings.streamEnabled,
                    modelProvider = session.modelProvider,
                    model = session.model,
                    attachments = selectedAttachments,
                )
                _uiState.update { it.copy(pendingAttachments = emptyList()) }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message ?: "Failed to submit") }
            }
        }
    }

    fun uploadAttachment(uri: Uri) {
        val sessionKey = uiState.value.selectedSessionKey
        viewModelScope.launch {
            runBusy {
                val resolver = getApplication<Application>().contentResolver
                val mimeType = resolver.getType(uri)
                val filename = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
                    .ifBlank { "attachment" }
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read selected file")
                }
                val attachment = withContext(Dispatchers.IO) {
                    archiveApi.upload(
                        baseUrl = currentSettings.baseUrl,
                        token = currentSettings.token,
                        bytes = bytes,
                        filename = filename,
                        mimeType = mimeType,
                        sessionKey = sessionKey,
                    )
                }
                _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
            }
        }
    }

    fun removePendingAttachment(archiveId: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.archiveId == archiveId })
        }
    }

    fun deleteSession(sessionKey: String) {
        viewModelScope.launch {
            runBusy {
                repository.deleteSession(sessionKey)
                var nextSelected: String? = null
                val previousSelected = uiState.value.selectedSessionKey
                _uiState.update { state ->
                    val sessions = state.sessions.filterNot { it.sessionKey == sessionKey }
                    nextSelected = state.selectedSessionKey
                        ?.takeUnless { it == sessionKey }
                        ?: sessions.firstOrNull()?.sessionKey
                    state.copy(
                        sessions = sessions,
                        selectedSessionKey = nextSelected,
                    )
                }
                settingsStore.saveLastSession(nextSelected)
                if (previousSelected == sessionKey && nextSelected != null) {
                    repository.subscribe(nextSelected!!)
                    val history = repository.loadHistory(nextSelected!!)
                    _uiState.update { state ->
                        state.copy(messagesBySession = state.messagesBySession + (nextSelected!! to history.messages))
                    }
                }
            }
        }
    }

    fun updateSession(
        sessionKey: String,
        title: String,
        modelProvider: String?,
        model: String?,
    ) {
        viewModelScope.launch {
            runBusy {
                repository.updateSession(sessionKey, title, modelProvider, model)
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { session ->
                            if (session.sessionKey == sessionKey) {
                                session.copy(
                                    title = title,
                                    modelProvider = modelProvider,
                                    model = model,
                                )
                            } else {
                                session
                            }
                        },
                    )
                }
            }
        }
    }

    fun refreshSettings() {
        viewModelScope.launch {
            currentSettings = settingsStore.settings.first()
            _uiState.update { it.copy(streamEnabled = currentSettings.streamEnabled) }
        }
    }

    private fun handleEvent(event: GatewayServerFrame.Event) {
        val payload = JsonObject(event.payload)
        when (event.event) {
            "session.message" -> payload.chatMessage()?.let { message ->
                _uiState.update { ChatStateReducer.reduceMessage(it, message) }
            }
            "session.stream.delta" -> {
                val sessionKey = payload.string("session_key") ?: uiState.value.selectedSessionKey
                val requestId = payload.string("request_id")
                val content = payload.string("content").orEmpty()
                if (sessionKey != null && requestId != null) {
                    _uiState.update { ChatStateReducer.reduceStreamDelta(it, sessionKey, requestId, content) }
                }
            }
            "session.stream.clear", "session.stream.done" -> {
                val requestId = payload.string("request_id")
                if (requestId != null) {
                    _uiState.update { state ->
                        state.copy(streamingMessages = state.streamingMessages - requestId)
                    }
                }
            }
        }
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true) }
        runCatching { block() }
            .onFailure { error -> _uiState.update { it.copy(statusMessage = error.message) } }
        _uiState.update { it.copy(isBusy = false) }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
