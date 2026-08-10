# CI

この文書は Lite Memo の GitHub Actions における実行条件、job 構成、カバレッジ計測、キャッシュ戦略、命名規約をまとめます。
ローカルで同じ検証を流すコマンドは [`docs/development-setup.md`](development-setup.md) を正本とします。

## 実行条件

- draft ではない Pull Request は、base branch にかかわらず CI の対象とする
- `develop` / `main` を base にする Pull Request では、静的解析と JVM Unit Test に加えて release / R8 build を検証する
- coverage は draft ではない Pull Request と、`main` への push で計測する
- draft の Pull Request は全 job を skip し、ready for review にした時点で CI と CodeQL を開始する
- ready for review から draft に戻した場合は、実行中の Pull Request の検証を中断する
- Pull Request の `closed` event は workflow の起動対象に含めない。merge 後は base branch への push だけで検証し、Pull Request と push の二重実行を防ぐ

## job 構成

- Static Analysis、Unit Test、Android Test を別 job で並列実行する
- Gradle Wrapper Validation で wrapper の改ざんを検知する
- GitHub Actions Validation で actionlint によるワークフロー検査を行う
- CodeQL と Dependabot は CI とは別に構成する

Gradle Wrapper Validation、GitHub Actions Validation、CodeQL などの workflow 固有チェックは
ローカルのコマンドでは再現しません。最終結果は GitHub Actions で確認します。

## カバレッジ計測（Kover）

Kover の集計対象は `app/build.gradle.kts` の `kover` ブロックで指定しています。
domain / data 層は `domain.model` / `domain.usecase` / `data.mapper` / `data.repository` などをパッケージ単位で選びます。
ここで挙げたパッケージは代表例であり、正確な集計対象は `classes(...)` の設定を確認します。
UI 層は `*ViewModel*` / `*UiState*` / `*UiResult*` / `*UiModel*` / `*UiDirection*` / `*UiMessage*` / `*UiStatus*` / `*UiType*` と、
役割を表す接尾語のパターンで選びます。
接尾語のパターンで選ぶため、UI 層はパッケージを移動しても集計対象は変わりません。

役割の接尾語に合わない名前の型を追加すると、集計対象から外れます。
外れても失敗しないため、集計したい型は既存の接尾語へ命名をそろえるか、`classes(...)` にパターンを追加します。

## キャッシュ

GitHub Actions の Gradle / AVD キャッシュは、長期運用する `main` の push で作成します。
`main` では通常の CI が未作成のキャッシュを作成します。
Pull Request では base branch 側の既存キャッシュを復元するだけとし、PR 固有の `refs/pull/.../merge` にはキャッシュを作成しません。
通常の CI と CodeQL の Gradle キャッシュは Enhanced Caching を使い、job ごとの build state を別々に保存します。
job と commit を区別した cache key と共有 artifact により、並列 job が同じ不変 key への保存を競合しないようにします。
AVD キャッシュの key には API level、target、architecture、設定版を含めます。
エミュレーター設定を変えた場合は末尾の設定版を更新して、新しい snapshot を作成します。
AVD キャッシュでは `restore-keys` による部分一致を使いません。
設定版を上げた意味がなくなり、古い設定の snapshot を復元してしまうためです。
設定版を上げた直後は cache miss になりますが、次の `main` への push で作り直されます。
push の run はキャッシュを作成できる唯一の経路のため、concurrency では push を中断しません。

## GitHub Actions の命名

- workflow の表示名は、対象領域を表す短い Title Case にする（例: `CI`、`CodeQL`）
- job ID は、対象と処理が分かる kebab-case にする（例: `unit-test`、`codeql-analysis`）
- job の表示名は job ID と同じ意味の Title Case にし、製品名の正式な表記を保つ（例: `Unit Test`、`CodeQL Analysis`）
- step の表示名は Title Case にし、動詞から始めて対象を明示する（例: `Run Unit Test`、`Validate Gradle Wrapper`）
  - `gradlew` のようなファイル名や識別子は、そのままの表記を保つ（例: `Make gradlew Executable`）
  - `Checkout` は action 名として定着しているため、動詞から始める規則の例外として許容する
- matrix を使う job は、GitHub が check 名へ matrix 値を自動で付加する。
  意図しない表記を避けるため、job の表示名へ明示的に含める（例: `CodeQL Analysis (${{ matrix.language }})`）
- fastlane の lane 名は snake_case とし、GitHub Actions の job ID と表記形式をそろえるためだけには変更しない
