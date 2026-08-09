package com.lambdarc.litememo.domain.repository

import kotlinx.coroutines.flow.Flow

interface TutorialProgressRepository {

    fun observeTutorialCompleted(): Flow<Boolean>

    suspend fun completeTutorial()

}
