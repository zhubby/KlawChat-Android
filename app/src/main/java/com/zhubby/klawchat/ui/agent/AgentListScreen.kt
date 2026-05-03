package com.zhubby.klawchat.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhubby.klawchat.data.gateway.WorkspaceSession
import com.zhubby.klawchat.ui.chat.ChatUiState
import com.zhubby.klawchat.ui.chat.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    state: ChatUiState,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onReconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("KlawChat") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionHeader(
                status = state.connectionStatus,
                message = state.statusMessage,
                onReconnect = onReconnect,
            )
            Button(
                onClick = onCreateSession,
                enabled = state.connectionStatus == ConnectionStatus.Connected && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("New Agent")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sessions, key = { it.sessionKey }) { session ->
                    AgentRow(
                        session = session,
                        selected = session.sessionKey == state.selectedSessionKey,
                        onClick = { onSelectSession(session.sessionKey) },
                        onDelete = { onDeleteSession(session.sessionKey) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeader(
    status: ConnectionStatus,
    message: String?,
    onReconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = message ?: "No status",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = onReconnect) {
                Text("Reconnect")
            }
        }
    }
}

@Composable
private fun AgentRow(
    session: WorkspaceSession,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title?.takeIf { it.isNotBlank() } ?: session.sessionKey,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(session.modelProvider, session.model).joinToString(" / ")
                        .ifBlank { "Default model" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selected) {
                Text("Selected", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.weight(0.1f))
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}
