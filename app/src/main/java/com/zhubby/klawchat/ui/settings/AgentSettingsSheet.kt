package com.zhubby.klawchat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhubby.klawchat.data.gateway.Provider
import com.zhubby.klawchat.data.gateway.WorkspaceSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsSheet(
    session: WorkspaceSession,
    providers: List<Provider>,
    onDismiss: () -> Unit,
    onSave: (title: String, modelProvider: String?, model: String?) -> Unit,
) {
    var title by remember(session.sessionKey) { mutableStateOf(session.title.orEmpty()) }
    var modelProvider by remember(session.sessionKey) { mutableStateOf(session.modelProvider.orEmpty()) }
    var model by remember(session.sessionKey) { mutableStateOf(session.model.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Agent Settings")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = modelProvider,
                onValueChange = { modelProvider = it },
                label = { Text("Provider") },
                placeholder = {
                    Text(providers.firstOrNull()?.id ?: "provider id")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = {
                    Text(providers.firstOrNull()?.defaultModel ?: "model")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            title.trim().ifBlank { session.sessionKey },
                            modelProvider.trim().ifBlank { null },
                            model.trim().ifBlank { null },
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
