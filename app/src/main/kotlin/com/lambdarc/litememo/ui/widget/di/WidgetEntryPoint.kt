package com.lambdarc.litememo.ui.widget.di

import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun observeRecentMemosUseCase(): ObserveRecentMemosUseCase
}
