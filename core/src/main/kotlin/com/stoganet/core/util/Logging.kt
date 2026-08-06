package com.stoganet.core.util

import android.util.Log

internal fun <T> Result<T>.logOnFailure(tag: String): Result<T> = onFailure { Log.w(tag, it.message, it) }
