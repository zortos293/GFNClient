package com.opencloudgaming.opennow

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val AUTH_REFRESH_WORK_NAME = "opennow-auth-token-refresh"
private const val AUTH_REFRESH_LOG_TAG = "OpenNOWAuthRefresh"
private const val AUTH_REFRESH_INTERVAL_MINUTES = 15L
private const val AUTH_REFRESH_FLEX_MINUTES = 5L
private const val AUTH_REFRESH_BACKOFF_MINUTES = 5L

internal fun AuthTokens.needsBackgroundRefresh(nowMs: Long = System.currentTimeMillis()): Boolean =
    expiresAt - nowMs < TOKEN_REFRESH_WINDOW_MS ||
        clientToken.isNullOrBlank() ||
        clientTokenExpiresAt == null ||
        clientTokenExpiresAt - nowMs < CLIENT_TOKEN_REFRESH_WINDOW_MS

internal fun authenticationRefreshClientIds(
    savedClientId: String?,
    browserClientId: String,
    deviceClientId: String,
): List<String> =
    listOfNotNull(
        savedClientId?.takeIf(String::isNotBlank),
        browserClientId,
        deviceClientId,
    ).distinct()

/**
 * A best-effort refresh must never crash an unrelated authenticated action. The existing session
 * can still produce the normal provider/API error, while coroutine cancellation must keep its
 * structured-concurrency semantics.
 */
internal suspend fun refreshedSessionOrFallback(
    fallback: AuthSession,
    refresh: suspend () -> AuthSession?,
    onFailure: (Throwable) -> Unit = {},
): AuthSession =
    try {
        refresh() ?: fallback
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
        fallback
    }

internal object AndroidAuthRefreshScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            AndroidAuthRefreshWorker::class.java,
            AUTH_REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            AUTH_REFRESH_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                AUTH_REFRESH_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            AUTH_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

internal class AndroidAuthRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as OpenNowApplication
        val authStore = application.authStore
        val activeSession = authStore.reload().let { state ->
            state.sessions.firstOrNull { it.user.userId == state.activeUserId }
                ?: state.sessions.firstOrNull()
        } ?: return Result.success()
        if (!activeSession.tokens.needsBackgroundRefresh()) return Result.success()

        return runCatching {
            val refreshed = application.authRepository.restore(
                throwOnRefreshFailure = true,
                removeExpiredSessionOnFailure = false,
            )
            if (refreshed?.tokens?.needsBackgroundRefresh() == true) {
                Result.retry()
            } else {
                Result.success()
            }
        }.getOrElse { error ->
            Log.w(AUTH_REFRESH_LOG_TAG, "Background token refresh failed; retrying", error)
            Result.retry()
        }
    }
}
