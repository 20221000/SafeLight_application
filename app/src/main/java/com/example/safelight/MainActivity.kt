package com.example.safelight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safelight.ui.nav.SafeLightRoot
import com.example.safelight.ui.theme.SafeLightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeLightTheme {
                SafeLightRoot()
            }
        }
    }
}
