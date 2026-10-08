package com.stoganet.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun rememberInitialFocusRequester(enabled: Boolean): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) focusRequester.requestFocus()
    }
    return focusRequester
}

@Composable
fun FocusAfterFirstFrame(target: FocusRequester, enabled: Boolean) {
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        currentTarget.requestFocus()
    }
}

fun Modifier.focusRequesterIf(condition: Boolean, focusRequester: FocusRequester?): Modifier =
    if (condition && focusRequester != null) focusRequester(focusRequester) else this
