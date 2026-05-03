package com.zhubby.klawchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhubby.klawchat.navigation.AppNavigation
import com.zhubby.klawchat.ui.theme.KlawchatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KlawchatTheme {
                AppNavigation()
            }
        }
    }
}