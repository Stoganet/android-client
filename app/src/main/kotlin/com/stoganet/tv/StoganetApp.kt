package com.stoganet.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.stoganet.tv.di.ServiceLocator
import timber.log.Timber

class StoganetApp :
    Application(),
    SingletonImageLoader.Factory {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        services = ServiceLocator(this)
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(services.httpClient))
        }
        .build()
}
