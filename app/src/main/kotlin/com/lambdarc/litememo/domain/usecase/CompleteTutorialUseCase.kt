package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.TutorialProgressRepository
import javax.inject.Inject

class CompleteTutorialUseCase @Inject constructor(
    private val tutorialProgressRepository: TutorialProgressRepository
) {

    suspend operator fun invoke() = tutorialProgressRepository.completeTutorial()

}
