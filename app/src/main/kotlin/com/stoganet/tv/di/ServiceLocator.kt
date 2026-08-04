package com.stoganet.tv.di

import android.content.Context
import com.stoganet.tv.data.auth.AuthRepository
import com.stoganet.tv.data.auth.TokenStore
import com.stoganet.tv.data.detail.DetailRepository
import com.stoganet.tv.data.home.HomeRepository
import com.stoganet.tv.data.library.LibraryRepository
import com.stoganet.tv.data.net.StoganetApi
import com.stoganet.tv.data.net.buildHttpClient
import io.ktor.client.HttpClient

class ServiceLocator(context: Context) {

    private val appContext: Context = context.applicationContext

    val tokenStore: TokenStore by lazy { TokenStore.create(appContext) }

    val httpClient: HttpClient by lazy { buildHttpClient(tokenStore) }

    private val stoganetApi: StoganetApi by lazy { StoganetApi(httpClient) }

    val authRepository: AuthRepository by lazy { AuthRepository(stoganetApi, tokenStore) }

    val homeRepository: HomeRepository by lazy { HomeRepository(stoganetApi) }

    val libraryRepository: LibraryRepository by lazy { LibraryRepository(stoganetApi) }

    val detailRepository: DetailRepository by lazy { DetailRepository(stoganetApi) }
}
