package com.opencloudgaming.opennow

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpenNowApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val httpClient by lazy(::defaultHttpClient)
    internal val authStore by lazy { AuthStore(this) }
    internal val authRepository by lazy { GfnAuthRepository(this, authStore, httpClient) }
    internal val localTvConnector by lazy { LocalTvConnector() }

    override fun onCreate() {
        super.onCreate()

        if (isTelevisionDevice()) {
            startupScope.launch {
                val settings = SettingsStore(this@OpenNowApplication).settings.value
                delay(TV_BACKGROUND_SERVICE_START_DELAY_MS)
                withContext(Dispatchers.Main) {
                    initializeBackgroundServices(settings)
                }
            }
        } else {
            initializeBackgroundServices(SettingsStore(this).settings.value)
        }
    }

    override fun onTerminate() {
        localTvConnector.close()
        super.onTerminate()
    }

    private fun initializeBackgroundServices(settings: AppSettings) {
        OpenNowAnalytics.setup(this, settings)
        AndroidAuthRefreshScheduler.schedule(this)
    }

    private fun isTelevisionDevice(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    private companion object {
        const val TV_BACKGROUND_SERVICE_START_DELAY_MS = 2_500L
    }
}
