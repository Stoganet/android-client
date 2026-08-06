package com.stoganet.core.data.library

import com.stoganet.core.api.model.LibraryListResponse
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure

private const val TAG = "LibraryRepository"

class LibraryRepository(private val api: StoganetApi) {
    suspend fun getLibrary(type: MediaType? = null, cursor: String? = null, limit: Int): Result<LibraryListResponse> =
        runCatching { api.getLibrary(type, cursor, limit) }.logOnFailure(TAG)
}
