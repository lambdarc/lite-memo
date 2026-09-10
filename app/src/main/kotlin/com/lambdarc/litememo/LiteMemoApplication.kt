package com.lambdarc.litememo

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.lambdarc.litememo.di.ApplicationScope
import com.lambdarc.litememo.domain.usecase.DeleteAbandonedPreparedExportsUseCase
import com.lambdarc.litememo.domain.usecase.DeleteUnreferencedImportImagesUseCase
import com.lambdarc.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import com.lambdarc.litememo.ui.widget.data.RECENT_MEMOS_LIMIT
import com.lambdarc.litememo.ui.widget.data.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val WIDGET_REFRESH_DEBOUNCE_MS = 500L
private const val APP_LOCK_RETRY_MAX_DELAY_MS = 30_000L
private const val WIDGET_REFRESH_TAG = "WidgetRefresh"
private const val IMPORT_CLEANUP_TAG = "ImportCleanup"
private const val EXPORT_CLEANUP_TAG = "ExportCleanup"

@HiltAndroidApp
class LiteMemoApplication : Application() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var observeRecentMemosUseCase: ObserveRecentMemosUseCase

    @Inject
    lateinit var observeAppLockEnabledUseCase: ObserveAppLockEnabledUseCase

    @Inject
    lateinit var deleteUnreferencedImportImagesUseCase: DeleteUnreferencedImportImagesUseCase

    @Inject
    lateinit var deleteAbandonedPreparedExportsUseCase: DeleteAbandonedPreparedExportsUseCase

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            MobileAds.initialize(this@LiteMemoApplication)
        }
        deleteUnreferencedImportImages()
        deleteAbandonedPreparedExports()
        observeMemosForWidgetRefresh()
        observeAppLockForWidgetRefresh()
    }

    private fun deleteAbandonedPreparedExports() {
        applicationScope.launch {
            runCatching {
                deleteAbandonedPreparedExportsUseCase()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(EXPORT_CLEANUP_TAG, "Abandoned prepared export cleanup failed")
            }
        }
    }

    private fun deleteUnreferencedImportImages() {
        applicationScope.launch {
            runCatching {
                deleteUnreferencedImportImagesUseCase()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(IMPORT_CLEANUP_TAG, "Abandoned import image cleanup failed")
            }
        }
    }

    private fun observeMemosForWidgetRefresh() {
        applicationScope.launch {
            observeRecentMemosUseCase(RECENT_MEMOS_LIMIT)
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        Log.w(WIDGET_REFRESH_TAG, "Memo observation failed; retrying", cause)
                        delay(WIDGET_REFRESH_DEBOUNCE_MS)
                        true
                    }
                }
                .drop(1)
                .distinctUntilChanged()
                .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                .collect {
                    refreshRecentMemoWidgets()
                }
        }
    }

    private fun observeAppLockForWidgetRefresh() {
        applicationScope.launch {
            observeAppLockEnabledUseCase()
                .retryAppLockObservation {
                    Log.w(WIDGET_REFRESH_TAG, "App lock observation failed; retrying", it)
                    refreshRecentMemoWidgets()
                }
                .collect {
                    refreshRecentMemoWidgets()
                }
        }
    }

    private suspend fun refreshRecentMemoWidgets() {
        runCatching {
            WidgetRefresher.refreshLists(this@LiteMemoApplication)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(WIDGET_REFRESH_TAG, "Widget refresh failed", error)
        }
    }
}

internal fun <T> Flow<T>.retryAppLockObservation(
    initialDelayMillis: Long = WIDGET_REFRESH_DEBOUNCE_MS,
    maxDelayMillis: Long = APP_LOCK_RETRY_MAX_DELAY_MS,
    onFailureEpisode: suspend (Throwable) -> Unit
): Flow<T> = flow {
    require(initialDelayMillis > 0)
    require(maxDelayMillis >= initialDelayMillis)
    var retryDelayMillis = initialDelayMillis
    var failureEpisodeReported = false

    emitAll(
        this@retryAppLockObservation
            .distinctUntilChanged()
            .onEach {
                retryDelayMillis = initialDelayMillis
                failureEpisodeReported = false
            }
            .retryWhen { error, _ ->
                if (error is CancellationException) return@retryWhen false
                if (!failureEpisodeReported) {
                    onFailureEpisode(error)
                    failureEpisodeReported = true
                }
                delay(retryDelayMillis)
                retryDelayMillis = if (retryDelayMillis >= maxDelayMillis / 2) {
                    maxDelayMillis
                } else {
                    retryDelayMillis * 2
                }
                true
            }
    )
}
