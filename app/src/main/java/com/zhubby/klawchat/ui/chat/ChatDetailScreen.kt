package com.zhubby.klawchat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhubby.klawchat.data.gateway.ArchiveAttachment
import com.zhubby.klawchat.data.gateway.ChatMessage
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPickAttachment: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAgentSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.selectedSession
    var input by remember(session?.sessionKey) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onPickAttachment)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                title = {
                    Column {
                        Text(
                            text = session?.title ?: session?.sessionKey ?: "Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = listOfNotNull(session?.modelProvider, session?.model)
                                .joinToString(" / ")
                                .ifBlank { "Gateway agent" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAgentSettings) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Agent settings")
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                input = input,
                enabled = session != null,
                pendingAttachments = state.pendingAttachments,
                onInputChange = { input = it },
                onAttach = { picker.launch("*/*") },
                onRemoveAttachment = onRemoveAttachment,
                onSend = {
                    onSend(input)
                    input = ""
                },
            )
        },
    ) { padding ->
        if (session == null) {
            EmptyChatView(modifier = Modifier.padding(padding))
        } else {
            MessageList(
                messages = state.selectedMessages,
                streaming = state.streamingMessages.values
                    .filter { it.sessionKey == session.sessionKey },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
fun EmptyChatView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(20.dp),
            )
        }
        Text(
            text = "Select an agent",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "Choose or create an agent to load its chat history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    streaming: List<StreamingMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + streaming.size
    val latestStreamingLength = streaming.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(itemCount, latestStreamingLength) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(messages, key = { index, message -> "${message.id}-$index" }) { _, message ->
            MessageBubble(message)
        }
        items(streaming, key = { it.requestId }) { stream ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AssistChip(onClick = {}, label = { Text("Streaming") })
                    Text(stream.content)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role.equals("user", ignoreCase = true)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = "${if (isUser) "You" else "Agent"} · ${message.relativeTimeText()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = if (isUser) 0.dp else 10.dp,
                end = if (isUser) 10.dp else 0.dp,
                bottom = 4.dp,
            ),
        )
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f),
            shape = RoundedCornerShape(
                topStart = 26.dp,
                topEnd = 26.dp,
                bottomStart = if (isUser) 26.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 26.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (message.attachments.isNotEmpty()) {
                    Text(
                        text = "${message.attachments.size} attachment(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun ChatMessage.relativeTimeText(nowMs: Long = System.currentTimeMillis()): String {
    val timestamp = timestampMs ?: return "just now"
    val elapsedSeconds = max(0, (nowMs - timestamp) / 1_000)
    return when {
        elapsedSeconds < 5 -> "just now"
        elapsedSeconds < 60 -> "$elapsedSeconds seconds ago"
        elapsedSeconds < 3_600 -> {
            val minutes = elapsedSeconds / 60
            "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        }
        elapsedSeconds < 86_400 -> {
            val hours = elapsedSeconds / 3_600
            "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        }
        else -> {
            val days = elapsedSeconds / 86_400
            "$days ${if (days == 1L) "day" else "days"} ago"
        }
    }
}

@Composable
private fun Composer(
    input: String,
    enabled: Boolean,
    pendingAttachments: List<ArchiveAttachment>,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pendingAttachments.forEach { attachment ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = attachment.filename ?: attachment.archiveId,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        IconButton(onClick = { onRemoveAttachment(attachment.archiveId) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove attachment")
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onAttach,
                        enabled = enabled,
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = "Attach")
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        placeholder = { Text("Message") },
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                    IconButton(
                        onClick = onSend,
                        enabled = enabled && (input.isNotBlank() || pendingAttachments.isNotEmpty()),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
