package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.TutorialProgressRepository
import javax.inject.Inject

class CompleteTutorialUseCase @Inject constructor(
    private val tutorialProgressRepository: TutorialProgressRepository
) {

    suspend operator fun invoke() = tutorialProgressRepository.completeTutorial()

}
