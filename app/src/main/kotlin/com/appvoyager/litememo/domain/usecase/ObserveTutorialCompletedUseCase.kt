package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.TutorialProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTutorialCompletedUseCase @Inject constructor(
    private val tutorialProgressRepository: TutorialProgressRepository
) {

    operator fun invoke(): Flow<Boolean> = tutorialProgressRepository.observeTutorialCompleted()

}
