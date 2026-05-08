# Agents

An **Agent** in KlawChat represents an independent AI conversation session. Each Agent owns its own conversation history, configuration, and state, and can interact with different AI providers and models via the Gateway WebSocket protocol.

## Core Concepts

### Agent vs Session

- **Agent** — UI-level abstraction; the visible conversation entity rendered by Compose screens
- **Session** — Backend identifier; a `sessionKey` uniquely identifies a conversation context
- Every Agent maps to a `WorkspaceSession` that carries metadata like title, model provider, and model name

### Architecture Overview

The Agent subsystem follows an MVI (Model-View-Intent) pattern:

```
UI (Compose) → ViewModel (intents) → Repository (gateway calls) → WebSocket (v1 JSON-RPC)
     ↑                                                           ↓
  StateFlow ← StateReducer ← Server Frames (notifications/events)
```

| Layer             | Key class             | Responsibility                                      |
|-------------------|-----------------------|-----------------------------------------------------|
| UI                | `AgentListScreen`     | Renders session list, connection status, actions    |
| UI                | `AgentSettingsSheet`  | Modal bottom sheet for title/provider/model editing |
| ViewModel         | `ChatViewModel`       | Orchestrates intents, holds `ChatUiState`           |
| State             | `ChatStateReducer`    | Pure reduce functions for state transitions          |
| Repository        | `ChatRepository`      | Gateway RPC calls (`createSession`, `submit`, etc.) |
| Network           | `GatewayWsClient`     | Ktor WebSocket client, v1 JSON-RPC frame routing    |
| Persistence       | `GatewaySettingsStore`| DataStore-backed settings (baseUrl, token, etc.)    |

## Data Models

### WorkspaceSession

```kotlin
@Serializable
data class WorkspaceSession(
    @SerialName("session_key") val sessionKey: String,
    val title: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val model: String? = null,
)
```

Serialized via `kotlinx.serialization` with `@SerialName` annotations matching the gateway's snake_case wire format. All nullable fields use `= null` defaults to tolerate partial server responses.

### Provider & ProviderCatalog

```kotlin
@Serializable
data class Provider(
    val id: String,
    val name: String? = null,
    @SerialName("default_model") val defaultModel: String? = null,
    val stream: Boolean = true,
    @SerialName("has_api_key") val hasApiKey: Boolean = false,
)

@Serializable
data class ProviderCatalog(
    val providers: List<Provider> = emptyList(),
    @SerialName("default_provider") val defaultProvider: String? = null,
)
```

Providers are fetched dynamically via `ChatRepository.listProviders()` and rendered in `AgentSettingsSheet` as suggestions.

### ChatMessage (internal UI model)

```kotlin
data class ChatMessage(
    val id: String,
    val sessionKey: String,
    val role: String,          // "user" | "assistant"
    val content: String,
    val timestampMs: Long? = null,
    val requestId: String? = null,
    val attachments: List<ArchiveAttachment> = emptyList(),
)
```

Not directly serialized from the wire — parsed from gateway JSON-RPC result payloads via the `JsonObject.chatMessage()` extension function.

## Creating an Agent

### Via UI

1. User taps "New Agent" button on `AgentListScreen`
2. `ChatViewModel.createSession()` fires an intent
3. `ChatRepository.createSession()` sends `session.create` RPC to Gateway
4. Server returns a new `WorkspaceSession` with a generated `sessionKey`
5. State is updated; the new session is auto-selected and subscribed

### Programmatically (from ViewModel)

```kotlin
viewModelScope.launch {
    val session = repository.createSession()           // RPC: session.create
    _uiState.update { state ->
        state.copy(
            sessions = listOf(session) + state.sessions,
            selectedSessionKey = session.sessionKey,
        )
    }
    selectSession(session.sessionKey)                  // RPC: session.subscribe + session.history.load
}
```

## Configuring an Agent

### Configurable Properties

| Property        | Description              | Example                          |
|-----------------|--------------------------|----------------------------------|
| `title`         | Display name             | "Code Review Assistant"          |
| `modelProvider` | AI provider ID           | "anthropic", "openai"            |
| `model`         | Specific model name      | "claude-opus-4-7", "gpt-4"       |

Configuration is edited via `AgentSettingsSheet` (a `ModalBottomSheet` Composable) and persisted by calling `ChatRepository.updateSession()`:

```kotlin
repository.updateSession(
    sessionKey = session.sessionKey,
    title = title.trim().ifBlank { session.sessionKey },
    modelProvider = modelProvider.trim().ifBlank { null },
    model = model.trim().ifBlank { null },
)
```

### Provider Configuration

Available providers are fetched at bootstrap:

```kotlin
val catalog = repository.listProviders()  // RPC: provider.list
// catalog.providers → list of Provider objects
// catalog.defaultProvider → fallback provider ID
```

## Interacting with an Agent

### Sending a Message

```kotlin
val response = repository.submit(
    sessionKey = session.sessionKey,
    input = "Hello, can you help me with…",
    stream = currentSettings.streamEnabled,
    modelProvider = session.modelProvider,
    model = session.model,
    attachments = selectedAttachments,
)
```

When `stream = true`, `submit()` returns `null` and the response is delivered asynchronously via server notifications (see Streaming below).

When `stream = false`, `submit()` blocks until the full response is received and parsed into a `ChatMessage`.

### Uploading Attachments

File attachments use `ArchiveApi` (OkHttp-based multipart upload) before submission:

```kotlin
val attachment = archiveApi.upload(
    baseUrl = currentSettings.baseUrl,
    token = currentSettings.token,
    bytes = fileBytes,
    filename = "document.pdf",
    mimeType = "application/pdf",
    sessionKey = sessionKey,
)
// attachment → ArchiveAttachment(archiveId, filename, mimeType, size)
```

### Streaming Responses

Agent responses are delivered as server notifications through `SharedFlow`:

```kotlin
// In ChatViewModel, events are collected from repository.events:
viewModelScope.launch {
    repository.events.collect { event ->
        handleEvent(event)  // routes to ChatStateReducer
    }
}
```

| Notification event          | Reducer function                | Effect                                  |
|----------------------------|---------------------------------|-----------------------------------------|
| `session.stream.delta`     | `reduceStreamDelta()`           | Appends text to `StreamingMessage`      |
| `session.stream.clear`     | `reduceStreamClear()`           | Clears streaming buffer                 |
| `session.stream.done`      | `reduceMessage()`               | Finalizes assistant message in history  |
| `session.message`          | `reduceMessage()`               | Adds a complete message (non-streaming) |

### Gateway Frame Protocol

All communication uses v1 JSON-RPC over WebSocket (Ktor client):

```kotlin
// Client → Server: request with ID
GatewayClientRequest(id, method, params)

// Client → Server: notification (no ID)
GatewayClientNotification(method, params)

// Server → Client: result matching a request ID
GatewayServerFrame.Result(id, result)

// Server → Client: event/notification (no ID)
GatewayServerFrame.Notification(method, params)
```

Frame routing in `GatewayWsClient`:
- Frames with `result` → matched to pending `CompletableDeferred` by ID
- Frames with `error` → complete pending deferreds with exception
- Frames with `method` + no `id` → emitted as `events` SharedFlow
- Frames with `method` + `id` → emitted as `reverseRequests` SharedFlow

## Lifecycle

```
Disconnected → Connecting → Connected → Initialized → Active → (Disconnected) → Deleted
```

| State                    | Description                                               |
|--------------------------|-----------------------------------------------------------|
| `Disconnected`           | No WebSocket connection; initial or post-close state      |
| `Connecting`             | Ktor WebSocket session opening                            |
| `Connected`              | Socket open; v1 `initialize` handshake in progress        |
| `Initialized`            | Handshake complete; ready for RPC calls                   |
| `Active`                 | Session subscribed; user is chatting                      |
| `Disconnected` (re-entry)| Connection lost; user can tap Reconnect                   |
| `Deleted`                | Session deleted via `session.delete`; data cleaned up     |

Connection state is exposed as `SharedFlow<GatewayConnectionState>` and mapped to `ConnectionStatus` in `ChatUiState` for Compose rendering.

## State Management

### ChatUiState

```kotlin
data class ChatUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val statusMessage: String? = null,
    val sessions: List<WorkspaceSession> = emptyList(),
    val selectedSessionKey: String? = null,
    val messagesBySession: Map<String, List<ChatMessage>> = emptyMap(),
    val streamingMessages: Map<String, StreamingMessage> = emptyMap(),
    val pendingAttachments: List<ArchiveAttachment> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val defaultProvider: String? = null,
    val streamEnabled: Boolean = true,
    val isBusy: Boolean = false,
)
```

### ChatStateReducer (pure functions)

All state mutations flow through `ChatStateReducer` — pure functions with no side effects:

| Function                  | Purpose                                              |
|---------------------------|------------------------------------------------------|
| `reduceLocalUserMessage`  | Insert optimistic local user message before RPC      |
| `reduceStreamDelta`       | Append streaming text; skip if snapshot exists       |
| `reduceStreamClear`       | Clear streaming buffer for a session/request         |
| `reduceMessage`           | Upsert a message; deduplicate by ID or requestId    |
| `reduceHistory`           | Merge loaded history with existing messages          |

## Best Practices

### Agent Naming

- Use descriptive titles to distinguish different-purpose Agents
- Examples: "Code Review Assistant", "Copywriting Expert", "Technical Advisor"

### Model Selection

| Scenario        | Recommended Provider | Recommended Model        |
|-----------------|----------------------|--------------------------|
| Complex reasoning | anthropic          | claude-opus-4-7          |
| Daily conversation | anthropic         | claude-sonnet-4-6        |
| Fast response   | anthropic            | claude-haiku-4-5         |
| Code generation | openai               | gpt-4                    |

### Session Management

- Delete idle Agents to free resources (`ChatViewModel.deleteSession()`)
- Export important conversations via `ArchiveApi` for archiving
- Each Agent's history is stored independently — switching sessions preserves per-session state

### Error Handling

- Gateway errors surface as `GatewayConnectionState.Failed(message)` → displayed in the connection header
- RPC failures in the ViewModel are caught with `runCatching` → `statusMessage` updated in UI state
- Unrecognized server frames are silently dropped to avoid crashing the reader loop

### Thread Safety

- `GatewayWsClient` uses `ConcurrentHashMap` for pending request tracking
- UI state flows through `MutableStateFlow` (thread-safe by design)
- Repository and ViewModel operations run on `viewModelScope` (main thread dispatcher)
- `ArchiveApi.upload()` is blocking OkHttp — wrap in `withContext(Dispatchers.IO)` when called from coroutines

## Source Files

| File                                      | Description                                       |
|-------------------------------------------|---------------------------------------------------|
| `ui/agent/AgentListScreen.kt`             | Agent list UI (Compose)                           |
| `ui/settings/AgentSettingsSheet.kt`       | Agent configuration bottom sheet                  |
| `ui/chat/ChatViewModel.kt`               | MVI ViewModel orchestrating all agent intents     |
| `ui/chat/ChatState.kt`                   | `ChatUiState`, `StreamingMessage`, `ChatStateReducer` |
| `ui/chat/ChatDetailScreen.kt`            | Conversation detail UI                            |
| `data/gateway/GatewayModels.kt`          | Serializable data models (`WorkspaceSession`, `Provider`, `ChatMessage`, etc.) |
| `data/gateway/GatewayFrames.kt`          | JSON-RPC frame definitions & deserializer         |
| `data/gateway/GatewayWsClient.kt`        | Ktor WebSocket client with v1 protocol handling   |
| `data/gateway/ChatRepository.kt`         | High-level gateway RPC operations                 |
| `data/gateway/GatewayEndpoint.kt`        | Endpoint URL builder (HTTP → WebSocket conversion)|
| `data/gateway/ArchiveApi.kt`             | OkHttp multipart file upload                      |
| `data/settings/GatewaySettingsStore.kt`  | DataStore preferences for connection settings     |
## Git Commit Guidelines

Commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) specification. Each commit should be one logical change.

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

- **Subject line**: Required, imperative mood, lowercase, no trailing period, max 72 chars
- **Body**: Optional, explains *what* and *why*, not *how*
- **Footer**: Optional, use for `BREAKING CHANGE:`, `Closes #123`, etc.

### Commit Types


| Type       | Description                                 |
| ---------- | ------------------------------------------- |
| `feat`     | New feature                                 |
| `fix`      | Bug fix                                     |
| `docs`     | Documentation changes                       |
| `style`    | Code style (formatting, semicolons, etc.)   |
| `refactor` | Code refactoring without behavior change    |
| `perf`     | Performance improvements                    |
| `test`     | Test additions or corrections               |
| `chore`    | Maintenance tasks, dependencies, tooling    |
| `ci`       | CI/CD configuration changes                 |
| `build`    | Build system or external dependency changes |
| `revert`   | Reverting a previous commit                 |


### Examples

```
feat(cli): add agent mode for one-shot requests

Closes #42

feat(core): implement reliability retry with exponential backoff

Add retry policy with configurable max attempts and base delay.
Idempotency keys prevent duplicate processing on retry.

BREAKING CHANGE: AgentLoop now requires ReliabilityConfig parameter

fix(gui): resolve timestamp formatting in panel display

docs: add git commit guidelines to agents.md
```

### Pull Request Guidelines

PRs should include:

- Purpose and impacted crates
- Test evidence (commands run + results)
- Config/doc updates when behavior changes
- Sample CLI output when user-facing behavior is modified
