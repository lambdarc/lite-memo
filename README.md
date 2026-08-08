# Lite Memo

日本語 | [English](README.en.md)

Lite Memo は Android 向けの軽量メモアプリです。
Kotlin / Jetpack Compose / Material 3 を中心に、Clean Architecture + MVVM で構成しています。

- パッケージ名: `com.lambdarc.litememo`
- メインモジュール: `:app`
- ビルドフレーバー: `dev` / `prod`

## 主な機能

- メモの作成・編集・一覧表示・検索（Room による永続化）
- お気に入り登録とごみ箱（ごみ箱のメモは 30 日後に自動削除）
- タグによる整理
- カレンダー表示
- メモへの画像添付
- ホーム画面ウィジェット（新規メモ / 最近のメモ）
- アプリロック（生体認証 / `androidx.biometric`）
- テーマ・表示設定（ライト / ダークモード対応）
- メモのエクスポート / インポート（画像を含む ZIP。内部の manifest は JSON）
- 日本語 / 英語のローカライズ対応

## 技術スタック

| 領域 | 採用技術 |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coil |
| ウィジェット | Jetpack Glance |
| アーキテクチャ | Clean Architecture + MVVM |
| DI | Hilt |
| 状態 / 非同期 | StateFlow, Coroutines |
| データ | Room（migration / スキーマエクスポート）, DataStore, kotlinx.serialization |
| セキュリティ | androidx.biometric |
| 監視 | Firebase Crashlytics |
| 広告 | Google Mobile Ads SDK (AdMob) |
| テスト | JUnit Jupiter（JVM）, JUnit 4 + AndroidX Test（instrumented）, MockK, Turbine, kotlinx-coroutines-test, Compose UI Test / Espresso |
| 静的解析 / カバレッジ | KtLint, detekt（Compose ルール含む）, Android Lint, Kover |
| ビルド | JDK 17, compileSdk 36.1 / minSdk 28 / targetSdk 36, R8 + ProGuard |
| CI | GitHub Actions, fastlane, CodeQL, Dependabot |

> バージョンの正確な値は `app/build.gradle.kts` と `gradle/libs.versions.toml` を参照してください。

## プロジェクト構成

```text
app/src/main/kotlin/com/lambdarc/litememo/
├── ui/      Compose 画面・ViewModel・UI state・ナビゲーション・テーマ・ウィジェット
├── domain/  model・値オブジェクト・UseCase・Repository interface
├── data/    Repository 実装・Room・DataStore・mapper・Hilt module・Export/Import
├── di/      アプリ全体の Hilt module
└── *.kt     LiteMemoApplication / MainActivity（entry point）
```

## 開発環境のセットアップ

### 前提

- JDK 17（Gradle の `jvmToolchain` が 17）
- 開発・動作確認には `dev` フレーバーを使用

### Git フックの有効化

クローン後に一度だけ実行し、pre-commit の KtLint フックを有効化します。

```sh
git config core.hooksPath .githooks
```

### ビルド / 実行

```sh
# デバッグビルド（dev フレーバー）
./gradlew :app:assembleDevDebug

# 端末 / エミュレータにインストール
./gradlew :app:installDevDebug
```

### テスト / Lint

```sh
# Unit Test（CI と同じ prod フレーバー）
./gradlew :app:testProdDebugUnitTest

# KtLint
./gradlew :app:ktlintCheck

# CI 相当（KtLint + detekt + Android Lint + Unit Test）を fastlane で実行
bundle exec fastlane android ci

# Instrumented Test / Compose UI Test（端末またはエミュレーターが必要）
bundle exec fastlane android android_test
```

## ビルドフレーバー

| フレーバー | applicationId | 用途 |
| --- | --- | --- |
| `dev` | `com.lambdarc.litememo.dev` | 開発用。debug 署名・AdMob は Google 公式テスト ID |
| `prod` | `com.lambdarc.litememo` | 本番用。本番 AdMob ID |

## リリース

- release ビルドは R8 minify + リソース圧縮 + ProGuard を適用します。
- リリース署名は `keystore.properties` 方式で注入します（詳細は `keystore.properties.example` を参照）。署名鍵・ストアパスワードなどの秘密情報はコミットしません。
- Crashlytics Gradle Plugin が release variant の mapping ファイルをアップロードします。

```sh
# 署名済み AAB の生成（keystore.properties 設置後）
./gradlew :app:bundleProdRelease
```

## ドキュメント

詳細は `docs/` を参照してください。

- [docs/project-overview.md](docs/project-overview.md) — プロジェクト概要・技術スタック
- [docs/architecture.md](docs/architecture.md) — アーキテクチャ方針
- [docs/development-setup.md](docs/development-setup.md) — 開発環境セットアップ
- [プライバシーポリシー](docs/privacy/index.html)

## ライセンス

[MIT License](LICENSE) © 2026 mshi-04
