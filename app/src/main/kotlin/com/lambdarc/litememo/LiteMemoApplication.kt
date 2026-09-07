package com.lambdarc.litememo

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.lambdarc.litememo.di.ApplicationScope
import com.lambdarc.litememo.domain.usecase.DeleteAbandonedPreparedExportsUseCase
import com.lambdarc.litememo.domain.usecase.DeleteUnreferencedImportImagesUseCase
import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import com.lambdarc.litememo.ui.widget.data.WidgetMemoLoader
import com.lambdarc.litememo.ui.widget.data.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val WIDGET_REFRESH_DEBOUNCE_MS = 500L
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
            WidgetMemoLoader(observeRecentMemosUseCase).observeRecent()
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
                    runCatching {
                        WidgetRefresher.refreshLists(this@LiteMemoApplication)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(WIDGET_REFRESH_TAG, "Widget refresh failed", error)
                    }
                }
        }
    }
}
