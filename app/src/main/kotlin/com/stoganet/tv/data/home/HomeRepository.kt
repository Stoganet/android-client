package com.stoganet.tv.data.home

import com.stoganet.tv.api.model.HomeResponse
import com.stoganet.tv.data.net.StoganetApi

class HomeRepository(private val api: StoganetApi) {
    suspend fun getHome(): Result<HomeResponse> = runCatching { api.getHome() }
}
