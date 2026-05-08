package com.zhubby.klawchat.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
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
import com.zhubby.klawchat.data.gateway.streamDeltaText
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
                        GatewayConnectionState.Initialized -> it.copy(
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
            repository.events.collect { event ->
                runCatching { handleEvent(event) }
                    .onFailure { error ->
                        Log.w("KlawChatGateway", "Failed to handle ${event.method}", error)
                        _uiState.update { it.copy(statusMessage = error.message ?: "Failed to handle gateway event") }
                    }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            runCatching {
                repository.connect(GatewayEndpoint(currentSettings.baseUrl, currentSettings.token))
                // v1: initialize is done inside connectAndWait, no separate ping needed
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
                    ChatStateReducer.reduceHistory(state, sessionKey, history.messages)
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
                val displayContent = trimmed.ifEmpty { "Attachment" }
                _uiState.update {
                    ChatStateReducer.reduceLocalUserMessage(
                        state = it,
                        sessionKey = session.sessionKey,
                        content = displayContent,
                        attachments = selectedAttachments,
                    )
                }
                val response = repository.submit(
                    sessionKey = session.sessionKey,
                    input = displayContent,
                    stream = currentSettings.streamEnabled,
                    modelProvider = session.modelProvider,
                    model = session.model,
                    attachments = selectedAttachments,
                )
                response?.let { message ->
                    _uiState.update { ChatStateReducer.reduceMessage(it, message) }
                }
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
                val attachment = archiveApi.upload(
                    baseUrl = currentSettings.baseUrl,
                    token = currentSettings.token,
                    bytes = bytes,
                    filename = filename,
                    mimeType = mimeType,
                    sessionKey = sessionKey,
                )
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
                        ChatStateReducer.reduceHistory(state, nextSelected!!, history.messages)
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

    /** v1: handle notification events using method field instead of event field */
    private fun handleEvent(event: GatewayServerFrame.Notification) {
        val params = event.params
        when (event.method) {
            // v1: agent message streaming
            "item/agentMessage/delta" -> {
                val sessionKey = params.string("session_id") ?: uiState.value.selectedSessionKey
                val turnId = params.string("turn_id") ?: sessionKey
                val content = params.streamDeltaText()
                if (sessionKey != null && turnId != null) {
                    _uiState.update { ChatStateReducer.reduceStreamDelta(it, sessionKey, turnId, content) }
                }
            }

            // v1: clear streaming buffer
            "item/agentMessage/clear" -> {
                val sessionKey = params.string("session_id") ?: uiState.value.selectedSessionKey
                val turnId = params.string("turn_id")
                _uiState.update { state ->
                    ChatStateReducer.reduceStreamClear(state, sessionKey, turnId)
                }
            }

            // v1: item completed (includes assistant message finalization)
            "item/completed" -> {
                val item = params["item"]?.let { it as? kotlinx.serialization.json.JsonObject }
                val message = (params + (item ?: emptyMap())).chatMessage(
                    fallbackSessionKey = params.string("session_id"),
                )
                logParsedMessage(event.method, params, message)
                if (message != null) {
                    _uiState.update { ChatStateReducer.reduceMessage(it, message) }
                }
            }

            // v1: turn completed — finalize streaming if no message was delivered
            "turn/completed",
            "turn/failed",
            "turn/interrupted" -> {
                val sessionKey = params.string("session_id") ?: uiState.value.selectedSessionKey
                val turnId = params.string("turn_id")
                // Clear any remaining streaming content
                _uiState.update { state ->
                    ChatStateReducer.reduceStreamClear(state, sessionKey, turnId)
                }
            }

            // v1: session subscribed / unsubscribed notifications
            "session/subscribed",
            "session/unsubscribed" -> {
                Log.d("KlawChatGateway", "${event.method}: ${params.string("session_key")}")
            }

            // v1: legacy session.message compatibility (if server still sends old format)
            "session.message" -> {
                val message = params.chatMessage()
                logParsedMessage(event.method, params, message)
                message?.let {
                    _uiState.update { state -> ChatStateReducer.reduceMessage(state, it) }
                }
            }

            // v1: legacy stream notifications compatibility
            "session.stream.delta" -> {
                val sessionKey = params.string("session_key") ?: uiState.value.selectedSessionKey
                val requestId = params.string("request_id") ?: sessionKey
                val content = params.streamDeltaText()
                if (sessionKey != null && requestId != null) {
                    _uiState.update { ChatStateReducer.reduceStreamDelta(it, sessionKey, requestId, content) }
                }
            }

            "session.stream.clear" -> {
                val sessionKey = params.string("session_key") ?: uiState.value.selectedSessionKey
                val requestId = params.string("request_id")
                _uiState.update { state ->
                    ChatStateReducer.reduceStreamClear(state, sessionKey, requestId)
                }
            }

            "session.stream.done" -> {
                val message = params.chatMessage()
                logParsedMessage(event.method, params, message)
                if (message != null) {
                    _uiState.update { ChatStateReducer.reduceMessage(it, message) }
                } else {
                    val sessionKey = params.string("session_key") ?: uiState.value.selectedSessionKey
                    val requestId = params.string("request_id")
                    _uiState.update { state ->
                        ChatStateReducer.reduceStreamClear(state, sessionKey, requestId)
                    }
                }
            }

            // Catch-all for other notification methods (item/started, item/updated, reasoning/delta, plan/delta, etc.)
            else -> {
                Log.d("KlawChatGateway", "Unhandled notification: ${event.method}")
            }
        }
    }

    private fun logParsedMessage(
        eventName: String,
        params: kotlinx.serialization.json.JsonObject,
        message: com.zhubby.klawchat.data.gateway.ChatMessage?,
    ) {
        Log.d(
            "KlawChatGateway",
            "$eventName parsed=${message != null} role=${message?.role} contentLength=${message?.content?.length ?: 0} keys=${params.keys}",
        )
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
