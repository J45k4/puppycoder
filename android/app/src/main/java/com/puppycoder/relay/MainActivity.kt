package com.puppycoder.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.puppycoder.relay.ui.RelayApp
import com.puppycoder.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RelayTheme {
                RelayApp()
            }
        }
    }
}
