package com.stoganet.core.data.home

import com.stoganet.core.api.model.HomeResponse
import com.stoganet.core.data.net.StoganetApi

class HomeRepository(private val api: StoganetApi) {
    suspend fun getHome(): Result<HomeResponse> = runCatching { api.getHome() }
}
