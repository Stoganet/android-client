package com.stoganet.core.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// runCatching also catches cancellation, which turns a cancelled call into a failed one
internal suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    runCatching(block).onFailure { currentCoroutineContext().ensureActive() }
