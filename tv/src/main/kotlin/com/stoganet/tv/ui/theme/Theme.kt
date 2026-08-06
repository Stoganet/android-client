package com.stoganet.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StoganetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        M3MaterialTheme(colorScheme = m3DarkColorScheme(), content = content)
    }
}
