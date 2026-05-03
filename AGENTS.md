# Agents

KlawChat 中的 **Agent** 代表一个独立的 AI 对话会话。每个 Agent 拥有自己的对话历史、配置和状态，可与不同的 AI Provider 和模型进行交互。

## 核心概念

### Agent 与 Session

- **Agent**: UI 层面的抽象，用户可见的对话实体
- **Session**: 后端标识符，通过 `sessionKey` 唯一标识一个对话上下文
- 每个 Agent 对应一个 `WorkspaceSession`，包含标题、模型配置等元数据

## 数据模型

```kotlin
@Serializable
data class WorkspaceSession(
    val sessionKey: String,      // 唯一标识符
    val title: String?,          // 用户自定义标题
    val createdAtMs: Long?,      // 创建时间戳
    val modelProvider: String?,  // AI 提供商 (如 anthropic, openai)
    val model: String?,          // 具体模型 (如 claude-opus-4-7)
)
```

## 创建 Agent

### 从 UI 创建

1. 在 Agent 列表页点击 "New Agent" 按钮
2. 系统通过 Gateway 创建新 Session
3. 自动生成 `sessionKey` 并返回

### 程序化创建

```kotlin
// 发送 create_session 帧到 Gateway
val frame = GatewayFrame.CreateSession(
    sessionKey = UUID.randomUUID().toString(),
    title = "新 Agent",
    modelProvider = "anthropic",
    model = "claude-sonnet-4-6"
)
```

## 配置 Agent

### 可配置项

| 属性 | 说明 | 示例 |
|------|------|------|
| title | Agent 显示名称 | "代码助手" |
| modelProvider | AI 提供商 ID | "anthropic", "openai" |
| model | 具体模型名称 | "claude-opus-4-7" |

### Provider 配置

可用的 Provider 通过 Gateway 动态获取：

```kotlin
@Serializable
data class Provider(
    val id: String,              // provider 标识
    val name: String?,           // 显示名称
    val defaultModel: String?,   // 默认模型
    val stream: Boolean,         // 是否支持流式响应
    val hasApiKey: Boolean,      // 是否已配置 API Key
)
```

## 与 Agent 交互

### 发送消息

```kotlin
val message = GatewayFrame.SendMessage(
    sessionKey = agent.sessionKey,
    content = "你好，请帮我...",
    attachments = emptyList()
)
webSocketClient.send(message)
```

### 接收响应

Agent 响应通过 WebSocket 流式返回：

```kotlin
// 文本块
GatewayFrame.TextDelta(sessionKey, delta, finishReason)

// 工具调用
GatewayFrame.ToolUse(sessionKey, toolUse)

// 错误
GatewayFrame.Error(sessionKey, message)
```

## 生命周期

```
Created → Connected → Active → (Disconnected) → Deleted
```

| 状态 | 说明 |
|------|------|
| Created | Session 已在后端创建，等待首次连接 |
| Connected | WebSocket 连接正常，可收发消息 |
| Active | 正在进行对话 |
| Disconnected | 连接中断，可尝试重连 |
| Deleted | Session 已删除，数据清理完成 |

## 最佳实践

### Agent 命名

- 使用描述性标题区分不同用途的 Agent
- 示例："代码审查助手"、"文案创作专家"、"技术顾问"

### 模型选择

| 场景 | 推荐 Provider | 推荐 Model |
|------|---------------|------------|
| 复杂推理 | anthropic | claude-opus-4-7 |
| 日常对话 | anthropic | claude-sonnet-4-6 |
| 快速响应 | anthropic | claude-haiku-4-5 |
| 代码生成 | openai | gpt-4 |

### 会话管理

- 长期不用的 Agent 建议及时删除，释放资源
- 重要对话可导出归档（通过 ArchiveApi）
- 每个 Agent 的对话历史独立存储，互不影响

## 相关文件

| 文件 | 说明 |
|------|------|
| `AgentListScreen.kt` | Agent 列表 UI |
| `AgentSettingsSheet.kt` | Agent 配置界面 |
| `GatewayModels.kt` | 数据模型定义 |
| `GatewayWsClient.kt` | WebSocket 通信 |
| `ChatRepository.kt` | 消息存储与同步 |
