package com.stoganet.core

import android.net.Uri

object AppRoutes {
    const val HOME = "home"
    const val LIBRARY_MOVIES = "library/movies"
    const val LIBRARY_TV = "library/tv"
    const val SEARCH = "search"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{id}?positionMs={positionMs}"

    fun detail(id: String) = "detail/${Uri.encode(id)}"
    fun player(id: String, positionMs: Long = 0L): String {
        val base = "player/${Uri.encode(id)}"
        return if (positionMs > 0L) "$base?positionMs=$positionMs" else base
    }
}
