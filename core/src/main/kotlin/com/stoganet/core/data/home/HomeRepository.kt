package com.stoganet.core.data.home

import com.stoganet.core.api.model.HomeResponse
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure
import com.stoganet.core.util.suspendRunCatching

private const val TAG = "HomeRepository"

class HomeRepository(private val api: StoganetApi) {
    suspend fun getHome(): Result<HomeResponse> = suspendRunCatching { api.getHome() }.logOnFailure(TAG)
}
