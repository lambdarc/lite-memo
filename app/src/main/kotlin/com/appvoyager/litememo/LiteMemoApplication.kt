package com.appvoyager.litememo

import android.app.Application
import android.util.Log
import com.appvoyager.litememo.di.ApplicationScope
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import com.appvoyager.litememo.domain.repository.MemoImportArchiveRepository
import com.appvoyager.litememo.domain.usecase.ObserveRecentMemosUseCase
import com.appvoyager.litememo.ui.widget.data.WidgetMemoLoader
import com.appvoyager.litememo.ui.widget.data.WidgetRefresher
import com.google.android.gms.ads.MobileAds
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
    lateinit var memoImportArchiveRepository: MemoImportArchiveRepository

    @Inject
    lateinit var memoExportArchiveRepository: MemoExportArchiveRepository

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
                memoExportArchiveRepository.deleteAbandonedPreparedExports()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(EXPORT_CLEANUP_TAG, "Abandoned prepared export cleanup failed")
            }
        }
    }

    private fun deleteUnreferencedImportImages() {
        applicationScope.launch {
            runCatching {
                memoImportArchiveRepository.deleteUnreferencedImportImages()
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
