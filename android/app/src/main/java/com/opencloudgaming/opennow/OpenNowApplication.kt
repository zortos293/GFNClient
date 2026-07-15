package com.opencloudgaming.opennow

import android.app.Application

class OpenNowApplication : Application() {
    internal val httpClient by lazy(::defaultHttpClient)
    internal val authStore by lazy { AuthStore(this) }
    internal val authRepository by lazy { GfnAuthRepository(this, authStore, httpClient) }

    override fun onCreate() {
        super.onCreate()

        OpenNowAnalytics.setup(this, SettingsStore(this).settings.value)
        AndroidAuthRefreshScheduler.schedule(this)
    }
}
