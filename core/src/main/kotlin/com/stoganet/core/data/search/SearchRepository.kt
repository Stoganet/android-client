package com.stoganet.core.data.search

import com.stoganet.core.api.model.LibraryItem
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure

private const val TAG = "SearchRepository"

class SearchRepository(private val api: StoganetApi) {
    suspend fun search(query: String): Result<List<LibraryItem>> =
        runCatching { api.search(query).items }.logOnFailure(TAG)

    suspend fun requestMovie(id: String): Result<Unit> = runCatching { api.requestMovie(id) }.logOnFailure(TAG)
}
