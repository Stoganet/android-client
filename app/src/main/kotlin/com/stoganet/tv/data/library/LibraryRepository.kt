package com.stoganet.tv.data.library

import com.stoganet.tv.api.model.LibraryListResponse
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.net.StoganetApi

class LibraryRepository(private val api: StoganetApi) {
    suspend fun getLibrary(type: MediaType? = null, cursor: String? = null, limit: Int): Result<LibraryListResponse> =
        runCatching { api.getLibrary(type, cursor, limit) }
}
