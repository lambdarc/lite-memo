# Development Setup

クローン後に一度だけ実行が必要な設定と、開発時の前提をまとめます。
CI の実行条件、job 構成、カバレッジ計測、キャッシュ戦略は [`docs/ci.md`](ci.md) を正本とします。

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

- `app/src/dev/google-services.json`: `com.lambdarc.litememo.dev`
- `app/src/prod/google-services.json`: `com.lambdarc.litememo`

`app/google-services.json` は全 variant の fallback になるため、flavor 固有の設定ファイルは `src/<flavor>/` 配下に置きます。
release ビルドは R8 対象で、Crashlytics Gradle Plugin が release variant の mapping file upload task を生成します。

## Git フックの有効化

pre-commit フックとして KtLint 整形 + detekt 検査が設定されています。
クローン後に以下を実行してフックを有効にしてください。

```powershell
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

## ローカルでの検証

fastlane で静的解析と JVM Unit Test を手元で流せます。
`android ci` はローカル向けの共通セットであり、Pull Request の CI 全体とは一致しません。

```powershell
# 共通セット（KtLint → detekt → Android Lint → JVM Unit Test）
bundle exec fastlane android ci

# 個別に
bundle exec fastlane android static_analysis
bundle exec fastlane android ktlint
bundle exec fastlane android detekt
bundle exec fastlane android lint
bundle exec fastlane android unit_test
bundle exec fastlane android coverage

# R8 / minify ビルドまで検証する
bundle exec fastlane android unit_test release:true

# Instrumented Test / Compose UI Test（端末またはエミュレーターが必要）
bundle exec fastlane android android_test
```

`static_analysis` は KtLint / detekt / Android Lint を 1 回の Gradle 実行にまとめ、`--continue` を付けています。
途中のツールが失敗しても残りを実行し、3 つの指摘を 1 回で出すためです。

`unit_test` は JVM 上で実行され R8 / minify を通らないため、debug variant だけで全テストをカバーします。
`release:true` を渡したときは、R8 / minify ビルドが壊れていないかを `assembleProdRelease` で追加検証します。

Gradle から同じアプリ検証を直接実行する場合は次のとおりです。

```powershell
.\gradlew.bat :app:ktlintCheck
.\gradlew.bat :app:detekt
.\gradlew.bat :app:lintProdDebug
.\gradlew.bat :app:testProdDebugUnitTest
.\gradlew.bat :app:assembleProdRelease
.\gradlew.bat :app:koverXmlReportProdDebug :app:koverHtmlReportProdDebug

# 端末またはエミュレーターが必要
.\gradlew.bat :app:connectedDevDebugAndroidTest
```

Gradle Wrapper Validation、GitHub Actions Validation、CodeQL などの workflow 固有チェックは、
上記コマンドだけでは再現しません。最終結果は GitHub Actions で確認します。
