package com.stoganet.core.di

import android.content.Context
import com.stoganet.core.data.auth.AuthRepository
import com.stoganet.core.data.auth.TokenStore
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.home.HomeRepository
import com.stoganet.core.data.library.LibraryRepository
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.data.net.buildHttpClient
import com.stoganet.core.data.playback.PlaybackRepository
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

    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(stoganetApi) }
}
