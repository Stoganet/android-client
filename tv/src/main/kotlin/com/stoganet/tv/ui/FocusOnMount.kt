package com.stoganet.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

@Composable
fun rememberInitialFocusRequester(enabled: Boolean): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) focusRequester.requestFocus()
    }
    return focusRequester
}
