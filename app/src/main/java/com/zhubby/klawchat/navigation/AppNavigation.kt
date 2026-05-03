package com.zhubby.klawchat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zhubby.klawchat.ui.agent.AgentListScreen
import com.zhubby.klawchat.ui.chat.ChatDetailScreen
import com.zhubby.klawchat.ui.chat.ChatViewModel
import com.zhubby.klawchat.ui.settings.AgentSettingsSheet
import com.zhubby.klawchat.ui.settings.GatewaySettingsScreen
import com.zhubby.klawchat.ui.settings.SettingsViewModel

private object Routes {
    const val Agents = "agents"
    const val Chat = "chat"
    const val Settings = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var showAgentSettings by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Routes.Agents,
    ) {
        composable(Routes.Agents) {
            AgentListScreen(
                state = chatState,
                onCreateSession = chatViewModel::createSession,
                onSelectSession = { sessionKey ->
                    chatViewModel.selectSession(sessionKey)
                    navController.navigate(Routes.Chat)
                },
                onDeleteSession = chatViewModel::deleteSession,
                onReconnect = chatViewModel::connect,
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Chat) {
            ChatDetailScreen(
                state = chatState,
                onBack = { navController.popBackStack() },
                onSend = chatViewModel::sendMessage,
                onPickAttachment = chatViewModel::uploadAttachment,
                onRemoveAttachment = chatViewModel::removePendingAttachment,
                onOpenAgentSettings = { showAgentSettings = true },
            )
            val session = chatState.selectedSession
            if (showAgentSettings && session != null) {
                AgentSettingsSheet(
                    session = session,
                    providers = chatState.providers,
                    onDismiss = { showAgentSettings = false },
                    onSave = { title, provider, model ->
                        chatViewModel.updateSession(
                            sessionKey = session.sessionKey,
                            title = title,
                            modelProvider = provider,
                            model = model,
                        )
                    },
                )
            }
        }
        composable(Routes.Settings) {
            GatewaySettingsScreen(
                settings = settings,
                onBaseUrlChange = settingsViewModel::updateBaseUrl,
                onTokenChange = settingsViewModel::updateToken,
                onStreamEnabledChange = settingsViewModel::updateStreamEnabled,
                onSave = {
                    settingsViewModel.save()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
