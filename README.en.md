# Lite Memo

[日本語](README.md) | English

Lite Memo is a lightweight memo app for Android.
It is built around Kotlin / Jetpack Compose / Material 3, following Clean Architecture + MVVM.

- Package name: `com.appvoyager.litememo`
- Main module: `:app`
- Build flavors: `dev` / `prod`

## Features

- Create, edit, list, and search memos (persisted with Room)
- Favorites and trash (trashed memos are deleted automatically after 30 days)
- Organize with tags
- Calendar view
- Attach images to memos
- Home screen widgets (new memo / recent memos)
- App lock (biometric authentication / `androidx.biometric`)
- Theme and display settings (light / dark mode)
- Export / import memos (ZIP archive including images; the manifest inside is JSON)
- Localized in Japanese and English

## Tech Stack

| Area | Technology |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coil |
| Widgets | Jetpack Glance |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| State / Async | StateFlow, Coroutines |
| Data | Room (migration / schema export), DataStore, kotlinx.serialization |
| Security | androidx.biometric |
| Observability | Firebase Crashlytics |
| Ads | Google Mobile Ads SDK (AdMob) |
| Testing | JUnit Jupiter (JVM), JUnit 4 + AndroidX Test (instrumented), MockK, Turbine, kotlinx-coroutines-test, Compose UI Test / Espresso |
| Static analysis / Coverage | KtLint, detekt (including Compose rules), Android Lint, Kover |
| Build | JDK 17, compileSdk 36.1 / minSdk 28 / targetSdk 36, R8 + ProGuard |
| CI | GitHub Actions, fastlane, CodeQL, Dependabot |

> For exact versions, see `app/build.gradle.kts` and `gradle/libs.versions.toml`.

## Project Structure

```text
app/src/main/kotlin/com/appvoyager/litememo/
├── ui/      Compose screens, ViewModels, UI state, navigation, theme, widgets
├── domain/  models, value objects, use cases, repository interfaces
├── data/    repository implementations, Room, DataStore, mappers, Hilt modules, export/import
├── di/      app-wide Hilt modules
└── *.kt     LiteMemoApplication / MainActivity (entry points)
```

## Development Setup

### Prerequisites

- JDK 17 (Gradle `jvmToolchain` is 17)
- Use the `dev` flavor for development and verification

### Enable Git Hooks

Run once after cloning to enable the pre-commit KtLint hook.

```sh
git config core.hooksPath .githooks
```

### Build / Run

```sh
# Debug build (dev flavor)
./gradlew :app:assembleDevDebug

# Install on a device / emulator
./gradlew :app:installDevDebug
```

### Test / Lint

```sh
# Unit tests (prod flavor, same as CI)
./gradlew :app:testProdDebugUnitTest

# KtLint
./gradlew :app:ktlintCheck

# Run CI-equivalent checks (KtLint + detekt + Android Lint + Unit Test) via fastlane
bundle exec fastlane android ci

# Instrumented / Compose UI tests (requires a device or emulator)
bundle exec fastlane android android_test
```

## Build Flavors

| Flavor | applicationId | Purpose |
| --- | --- | --- |
| `dev` | `com.appvoyager.litememo.dev` | Development. Debug signing; AdMob uses Google's official test IDs |
| `prod` | `com.appvoyager.litememo` | Production. Production AdMob IDs |

## Release

- Release builds apply R8 minification + resource shrinking + ProGuard.
- Release signing is injected via `keystore.properties` (see `keystore.properties.example`). Secrets such as signing keys and store passwords are never committed.
- The Crashlytics Gradle Plugin uploads the mapping file for release variants.

```sh
# Build a signed AAB (after setting up keystore.properties)
./gradlew :app:bundleProdRelease
```

## Documentation

See `docs/` for details.

- [docs/project-overview.md](docs/project-overview.md) — Project overview and tech stack
- [docs/architecture.md](docs/architecture.md) — Architecture guidelines
- [docs/development-setup.md](docs/development-setup.md) — Development setup
- [Privacy Policy](docs/privacy/en.html)

## License

[MIT License](LICENSE) © 2026 mshi-04
