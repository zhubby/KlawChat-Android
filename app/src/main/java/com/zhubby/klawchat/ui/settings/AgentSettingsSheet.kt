package com.zhubby.klawchat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Agent Settings",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Model route and title",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
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
                shape = RoundedCornerShape(18.dp),
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
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        onSave(
                            title.trim().ifBlank { session.sessionKey },
                            modelProvider.trim().ifBlank { null },
                            model.trim().ifBlank { null },
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Text(
                        text = " Save",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
