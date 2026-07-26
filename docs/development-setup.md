# Development Setup

クローン後に一度だけ実行が必要な設定と、開発時の前提をまとめます。

## 前提

- JDK 17（Gradle の `jvmToolchain` は 17）
- Android SDK（compileSdk 36.1）
- fastlane を使う場合は、[`.ruby-version`](../.ruby-version) に合う Ruby と Bundler
- ビルドフレーバーは `dev` / `prod`。開発・動作確認は `dev` を使う

## Ruby / Bundler

fastlane を使う場合は、repository root で依存 gem を準備します。
Ruby と gem の具体的なバージョンは [`.ruby-version`](../.ruby-version) と
[`Gemfile.lock`](../Gemfile.lock) を正本とします。

```powershell
bundle install
```

## Firebase / Crashlytics

Crashlytics は `dev` / `prod` の両方に導入済みです。
Firebase 設定ファイルは flavor ごとに配置します。

- `app/src/dev/google-services.json`: `com.appvoyager.litememo.dev`
- `app/src/prod/google-services.json`: `com.appvoyager.litememo`

`app/google-services.json` は全 variant の fallback になるため、flavor 固有の設定ファイルは `src/<flavor>/` 配下に置きます。
release ビルドは R8 対象で、Crashlytics Gradle Plugin が release variant の mapping file upload task を生成します。

## Git フックの有効化

pre-commit フックとして KtLint 整形 + detekt 検査が設定されています。
クローン後に以下を実行してフックを有効にしてください。

```sh
git config core.hooksPath .githooks
```

これにより、`git commit` 時にステージ済みの Kotlin ファイルがある場合だけ、**そのステージ済みファイルに対して** KtLint 整形（`ktlintFormatPreCommit`）と detekt 検査（`detektPreCommit`）が実行されます。フックは Gradle デーモンとビルドキャッシュ（`org.gradle.caching=true`）を利用するため、2 回目以降は高速化されます。

- Kotlin ファイルを含むコミットで未ステージの tracked 変更がある場合は、意図しない整形や混入を避けるためコミットが中断されます。
- 整形後は、コミット開始時点でステージされていたファイルだけを再ステージします。KtLint がステージ外のファイルも変更した場合は、内容を確認してから再度コミットしてください。
- detekt が違反を検出した場合はコミットが中断されます。`app/build/reports/detekt/` のレポートを確認して修正してください（detekt は自動修正しません）。

## 静的解析（ktlint / detekt / Android Lint）

役割分担は次のとおりです。

- **ktlint**: コード整形（フォーマット）
- **detekt**: 書き方・複雑度・アンチパターン（+ Compose 特化ルール）
- **Android Lint**: Android 特有のバグ・非推奨 API・リソース・アクセシビリティ

detekt は baseline を使用せず、`maxIssues: 0` で検出した違反をすべて失敗として扱います。
Android Lint は baseline なしで実行し、警告もエラーとして扱います。

## ローカルでの共通チェック（任意）

日常的に静的解析と JVM Unit Test の共通部分を手元で流す場合は fastlane を使えます。
`android ci` はローカル向けの共通セットであり、Pull Request の CI 全体とは一致しません。

```sh
# 共通セット（KtLint → detekt → Android Lint → JVM Unit Test）
bundle exec fastlane android ci

# 個別に
bundle exec fastlane android static_analysis
bundle exec fastlane android ktlint
bundle exec fastlane android detekt
bundle exec fastlane android lint
bundle exec fastlane android unit_test
bundle exec fastlane android coverage

# Instrumented Test / Compose UI Test（端末またはエミュレーターが必要）
bundle exec fastlane android android_test
```

## Pull Request 前の主要なアプリ検証

`develop` / `main` を base にする Pull Request では、静的解析と JVM Unit Test に加えて
release / R8 build を検証します。coverage は base branch にかかわらず、すべての Pull Request と push で計測します。
GitHub Actions では Static Analysis、Unit Test、Android Test を別 job で並列実行します。
ローカルで主要なアプリ検証を再現するコマンドは次のとおりです。

```powershell
bundle exec fastlane android static_analysis
bundle exec fastlane android unit_test release:true
bundle exec fastlane android coverage

# 端末またはエミュレーターが使える場合
bundle exec fastlane android android_test
```

Gradle から同じアプリ検証を直接実行する場合は次のとおりです。

```sh
./gradlew :app:ktlintCheck
./gradlew :app:detekt
./gradlew :app:lintProdDebug
./gradlew :app:testProdDebugUnitTest
./gradlew :app:assembleProdRelease
./gradlew :app:koverXmlReportProdDebug :app:koverHtmlReportProdDebug

# 端末またはエミュレーターが必要
./gradlew :app:connectedDevDebugAndroidTest
```

Gradle Wrapper Validation、skill 同期、actionlint、CodeQL などの workflow 固有チェックは、
上記コマンドだけでは再現しません。最終結果は GitHub Actions で確認します。

## カバレッジ計測（Kover）

Kover の集計対象は `app/build.gradle.kts` の `kover` ブロックで指定しています。
domain / data 層は `domain.model` / `domain.usecase` / `data.mapper` / `data.repository` のようにパッケージ単位で選びます。
UI 層は `*ViewModel*` / `*UiState*` / `*UiResult*` / `*UiModel*` / `*UiDirection*` / `*UiMessage*` / `*UiStatus*` / `*UiType*` と、
役割を表す接尾語のパターンで選びます。
接尾語のパターンで選ぶため、UI 層はパッケージを移動しても集計対象は変わりません。

役割の接尾語に合わない名前の型を追加すると、集計対象から外れます。
外れても失敗しないため、集計したい型は既存の接尾語へ命名をそろえるか、`classes(...)` にパターンを追加します。

## CI キャッシュ

GitHub Actions の Gradle / AVD キャッシュは、長期運用する `main` / `develop` の push で作成します。
`main` / `develop` では通常の CI が未作成のキャッシュを作成します。
Pull Request では base branch 側の既存キャッシュを復元するだけとし、PR 固有の `refs/pull/.../merge` にはキャッシュを作成しません。
