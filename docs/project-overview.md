# Project Overview

この文書は Lite Memo の概要、主要な確認先、技術スタックをまとめます。

技術スタックの具体値は執筆時点のスナップショットです。バージョンの正確な値は、必ず
`app/build.gradle.kts` と `gradle/libs.versions.toml` を確認してください。

## 概要

Lite Memo は Android 向けの軽量メモアプリです。

現在の基本情報:

- パッケージ名: `com.appvoyager.litememo`
- ルートプロジェクト名: `Lite Memo`
- メインモジュール: `:app`
- ビルドフレーバー: `dev` / `prod`

## 現在の主な機能

- メモの作成・編集・検索・お気に入り・ごみ箱（30 日経過で自動削除）
- タグによる整理とカレンダー表示
- メモへの画像添付とホーム画面ウィジェット
- 画像を含む ZIP export/import、テーマ設定、アプリロック
- 日本語 / 英語のローカライズ

## 主な確認先

- `app/`: Android アプリ本体
- `app/src/main/kotlin/com/appvoyager/litememo/`: Kotlin ソース
  - `ui/`: Compose 画面、ViewModel、UI state、ナビゲーション、テーマ
  - `domain/`: model、値オブジェクト、UseCase、Repository interface、provider
  - `data/`: Repository 実装、Room、DataStore、mapper、データ層の Hilt module、Export/Import
  - `di/`: app 全体の構成値や Android 依存を組み立てる Hilt module
- `app/src/main/res/`: Android リソース（表示文字列は `strings.xml` に集約）
- `app/schemas/`: Room のエクスポート済みスキーマ
- `app/src/dev/google-services.json` / `app/src/prod/google-services.json`: Firebase 設定
- `gradle/libs.versions.toml`: 依存関係とプラグインのバージョン管理
- `.github/workflows/`: CI / CodeQL（設定済み）
- `fastlane/`: KtLint・テスト実行などの CI レーン

## 技術スタック

### Android / UI

- Kotlin
- Jetpack Compose
- Material 3
- Material 3 `NavigationBar` / Bottom Navigation
- Material Icons（Core / Extended）
- Navigation Compose
- Coil Compose（メモ画像サムネイル表示）
- Jetpack Glance（ホーム画面ウィジェット）
- ライト / ダークモード

### DI

- Hilt
- `hilt-lifecycle-viewmodel-compose`（Compose から ViewModel を注入）

### State / Async

- StateFlow
- Coroutines

### Data

- Room
- DataStore（Preferences）
- kotlinx.serialization（ZIP内のmanifest JSONに使用。単独JSON形式のExport / Importは非対応）

Room / DataStore の使い分けは [`docs/architecture.md`](architecture.md) の Data 方針を正本とします。

### Security

- androidx.biometric（アプリロック / 生体認証）

### Observability

- Firebase Crashlytics（クラッシュ収集）
- Google Services Gradle Plugin（flavor ごとの `google-services.json` を処理）

### Test

- JUnit Jupiter 6.x（JVM Unit Test）
- kotlinx-coroutines-test（`runTest`）
- MockK（モック）
- Turbine（`StateFlow` / event stream の検証）
- JUnit 4 / AndroidX Test（instrumented test）
- Compose UI Test / Espresso / Room testing（instrumented test）
- Kover（カバレッジ計測）

### Build

- JDK 17（jvmToolchain）
- compileSdk 36.1 / minSdk 28 / targetSdk 36
- release ビルド: R8 minify + リソース圧縮 + ProGuard
- Crashlytics Gradle Plugin（release の mapping file upload）
- KtLint（コード整形）
- detekt（静的解析。書き方・複雑度 + Compose 特化ルール `io.nlopez.compose.rules`）
- Android Lint（Android 特有の問題検出）

各ツールの役割分担としきい値は [`docs/development-setup.md`](development-setup.md) の静的解析を正本とします。

### CI

- GitHub Actions
  - CI: Static Analysis（KtLint / detekt / Android Lint）/ Unit Test / Android Test
  - Gradle Wrapper Validation（wrapper の改ざん検知）
  - actionlint（ワークフロー検査）
  - CodeQL、Dependabot
- fastlane（ローカル / CI の検証コマンドを実行）

実行コマンドと lane の正本は [`docs/development-setup.md`](development-setup.md) と
[`fastlane/Fastfile`](../fastlane/Fastfile) を確認してください。

### Monetization / Release

- AdMob（Google Mobile Ads SDK）
  - バナー: `LiteMemoApp` のボトムナビゲーション上部に、全タブ共通のアンカー型アダプティブバナーを表示
  - アプリ ID / 広告ユニット ID は flavor 別の `strings.xml` で管理
    - `dev`: Google 公式テスト ID（`app/src/dev/res`）
    - `prod`: 本番 ID（`app/src/prod/res`）

署名鍵・ストアパスワードなどリリース署名に関する秘密情報はコミットしません。

### Localization

- 日本語 / 英語対応

表示文字列の扱いは [`docs/implementation-guidelines.md`](implementation-guidelines.md) の Localization を正本とします。

## 判断の前提

- まず Gradle とソースを見て、現在導入済みのものを確認する
- 既存構成を優先し、必要以上に大きな再編をしない
- 構造方針は Clean Architecture + MVVM とし、詳細は `docs/architecture.md` を確認する
