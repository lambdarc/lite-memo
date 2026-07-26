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

draft ではない Pull Request は、base branch にかかわらず CI の対象です。
`develop` / `main` を base にする Pull Request では、静的解析と JVM Unit Test に加えて
release / R8 build を検証します。coverage は draft ではない Pull Request と、
`main` / `develop` への push で計測します。
GitHub Actions では Static Analysis、Unit Test、Android Test を別 job で並列実行します。

Pull Request の `closed` event は workflow の起動対象に含めません。
merge 後は base branch への push だけで検証し、Pull Request と push の二重実行を防ぎます。
draft の Pull Request は全 job を skip し、ready for review にした時点で CI と CodeQL を開始します。
ready for review から draft に戻した場合は、実行中の Pull Request の検証を中断します。
ローカルで主要なアプリ検証を再現するコマンドは次のとおりです。

```powershell
bundle exec fastlane android static_analysis
bundle exec fastlane android unit_test release:true
bundle exec fastlane android coverage

# 端末またはエミュレーターが使える場合
bundle exec fastlane android android_test
```

`static_analysis` は KtLint / detekt / Android Lint を 1 回の Gradle 実行にまとめ、`--continue` を付けています。
途中のツールが失敗しても残りを実行し、3 つの指摘を 1 回で出すためです。

`unit_test` は JVM 上で実行され R8 / minify を通らないため、debug variant だけで全テストをカバーします。
`release:true` を渡したときは、R8 / minify ビルドが壊れていないかを `assembleProdRelease` で追加検証します。

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

Gradle Wrapper Validation、skill 同期、GitHub Actions Validation、CodeQL などの workflow 固有チェックは、
上記コマンドだけでは再現しません。最終結果は GitHub Actions で確認します。

### GitHub Actions の命名

- workflow の表示名は、対象領域を表す短い Title Case にする（例: `CI`、`CodeQL`）
- job ID は、対象と処理が分かる kebab-case にする（例: `unit-test`、`codeql-analysis`）
- job の表示名は job ID と同じ意味の Title Case にし、製品名の正式な表記を保つ（例: `Unit Test`、`CodeQL Analysis`）
- step の表示名は Title Case にし、動詞から始めて対象を明示する（例: `Run Unit Test`、`Validate Gradle Wrapper`）
  - `gradlew` のようなファイル名や識別子は、そのままの表記を保つ（例: `Make gradlew Executable`）
  - `Checkout` は action 名として定着しているため、動詞から始める規則の例外として許容する
- matrix を使う job は、GitHub が check 名へ matrix 値を自動で付加する。
  意図しない表記を避けるため、job の表示名へ明示的に含める（例: `CodeQL Analysis (${{ matrix.language }})`）
- fastlane の lane 名は snake_case とし、GitHub Actions の job ID と表記形式をそろえるためだけには変更しない

## カバレッジ計測（Kover）

Kover の集計対象は `app/build.gradle.kts` の `kover` ブロックで指定しています。
domain / data 層は `domain.model` / `domain.usecase` / `data.mapper` / `data.repository` などをパッケージ単位で選びます。
ここで挙げたパッケージは代表例であり、正確な集計対象は `classes(...)` の設定を確認します。
UI 層は `*ViewModel*` / `*UiState*` / `*UiResult*` / `*UiModel*` / `*UiDirection*` / `*UiMessage*` / `*UiStatus*` / `*UiType*` と、
役割を表す接尾語のパターンで選びます。
接尾語のパターンで選ぶため、UI 層はパッケージを移動しても集計対象は変わりません。

役割の接尾語に合わない名前の型を追加すると、集計対象から外れます。
外れても失敗しないため、集計したい型は既存の接尾語へ命名をそろえるか、`classes(...)` にパターンを追加します。

## CI キャッシュ

GitHub Actions の Gradle / AVD キャッシュは、長期運用する `main` / `develop` の push で作成します。
`main` / `develop` では通常の CI が未作成のキャッシュを作成します。
Pull Request では base branch 側の既存キャッシュを復元するだけとし、PR 固有の `refs/pull/.../merge` にはキャッシュを作成しません。
通常の CI と CodeQL の Gradle キャッシュは Enhanced Caching を使い、job ごとの build state を別々に保存します。
job と commit を区別した cache key と共有 artifact により、並列 job が同じ不変 key への保存を競合しないようにします。
AVD キャッシュの key には API level、target、architecture、設定版を含めます。
エミュレーター設定を変えた場合は末尾の設定版を更新して、新しい snapshot を作成します。
AVD キャッシュでは `restore-keys` による部分一致を使いません。
設定版を上げた意味がなくなり、古い設定の snapshot を復元してしまうためです。
設定版を上げた直後は cache miss になりますが、次の `main` / `develop` への push で作り直されます。
push の run はキャッシュを作成できる唯一の経路のため、concurrency では push を中断しません。
