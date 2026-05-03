package com.zhubby.klawchat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhubby.klawchat.data.gateway.ChatMessage

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
        topBar = {
            TopAppBar(
                title = { Text(session?.title ?: session?.sessionKey ?: "Chat") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = onOpenAgentSettings) { Text("Agent") } },
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
    ) {
        Text("Select or create an Agent to start chatting.")
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    streaming: List<StreamingMessage>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
        items(streaming, key = { it.requestId }) { stream ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AssistChip(onClick = {}, label = { Text("assistant streaming") })
                    Text(stream.content)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message.role,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(text = message.content)
            if (message.attachments.isNotEmpty()) {
                Text(
                    text = "${message.attachments.size} attachment(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    enabled: Boolean,
    pendingAttachments: List<com.zhubby.klawchat.data.gateway.ArchiveAttachment>,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pendingAttachments.forEach { attachment ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = attachment.filename ?: attachment.archiveId,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onRemoveAttachment(attachment.archiveId) }) {
                    Text("Remove")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
            )
            TextButton(
                onClick = onAttach,
                enabled = enabled,
            ) {
                Text("Attach")
            }
            Button(
                onClick = onSend,
                enabled = enabled && (input.isNotBlank() || pendingAttachments.isNotEmpty()),
            ) {
                Text("Send")
            }
        }
    }
}
