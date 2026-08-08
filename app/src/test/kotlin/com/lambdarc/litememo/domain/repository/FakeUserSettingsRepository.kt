package com.lambdarc.litememo.domain.repository

class FakeUserSettingsRepository(
    display: FakeDisplaySettingsRepository = FakeDisplaySettingsRepository(),
    appLock: FakeAppLockSettingsRepository = FakeAppLockSettingsRepository(),
    tutorial: FakeTutorialProgressRepository = FakeTutorialProgressRepository()
) : DisplaySettingsRepository by display,
    AppLockSettingsRepository by appLock,
    TutorialProgressRepository by tutorial
