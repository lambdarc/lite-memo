package com.lambdarc.litememo.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTutorialProgressRepository : TutorialProgressRepository {

    private val tutorialCompleted = MutableStateFlow(false)

    override fun observeTutorialCompleted(): Flow<Boolean> = tutorialCompleted

    override suspend fun completeTutorial() {
        tutorialCompleted.value = true
    }
}
