package com.example.safelight.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.safelight.ui.theme.SafeLightTheme

/** 아직 옮기지 않은 화면 자리. 탭 이동이 되는지 확인하는 용도다. */
@Composable
fun PlaceholderScreen(title: String, note: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = SafeLightTheme.colors.textStrong)
        Text(note, style = MaterialTheme.typography.bodyMedium, color = SafeLightTheme.colors.textMuted)
    }
}
