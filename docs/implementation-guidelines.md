# Implementation Guidelines

この文書は実装時の基本方針をまとめます。
詳細設計は、必要になった時点で別文書またはコード上で明確にしてください。

## UI

- UI は Jetpack Compose と Material 3 を基本にする
- 下部主要導線は Material 3 の `NavigationBar` を使う
- Compose Preview を壊さないように、UI コンポーネントは小さく分ける
- 画面間で再利用する共通 UI 部品は `ui/component` に配置する。新しく部品を作る前に `ui/component` 配下の既存部品を確認し、再利用できるものは重複実装しない
- ライト / ダーク両対応を前提にし、固定色の直書きを避ける

## Architecture

- 構造は Clean Architecture + MVVM、DI は Hilt を基本にする
- 依存方向、レイヤーの責務、パッケージ構成（禁止補助パッケージを含む）、Value Object、UseCase の粒度は [`docs/architecture.md`](architecture.md) を正本とする

## Kotlin

- 関数名は英語にする
- Route、Screen、ViewModel、主要な UiState / UiModel は、役割と同名の独立ファイルを基本とする
- UI 契約の命名規約（`XxxUiState` / `XxxUiResult` / `XxxUiEvent`）は [`docs/architecture.md`](architecture.md) に従う。補助 enum も `AppLockUiStatus` のように UI 契約であることを名前に含める
- 同じ責務を構成する小型の class / interface / object / enum は、ファイルが読みやすい範囲で関連する所有者ファイルへトップレベル宣言としてまとめてよい。300 行程度を分割検討の目安とするが、機械的な上限にはしない
- sealed hierarchy の直接の子型や、親に強く従属する private 実装型は親型へネストしてよい。外側のインスタンス参照が意図的に必要な場合を除き `inner` は使わない
- `companion object` と匿名 object 式は許容し、`app/src/test` と `app/src/androidTest` のテストコードは上記のファイル配置判断の対象外とする
- ガード条件は早期リターンで扱い、ネストを深くしない
- UseCase の公開操作は `operator fun invoke` で表す。処理が単一式（単一の `when` 式を含む）でその評価結果をそのまま返す場合は式形式、validation・ローカル変数・複数文や早期 return を含む場合や実行式と別の値を返す場合はブロック形式を使う。`Unit` 以外を返す場合は戻り値型を明示し、`Unit` は明示しない
- 取り得る種類だけを閉じ、実装型に別の superclass や複数 interface の実装余地を残す場合は `sealed interface` で表す。ただし、一覧系 UiState の `Loading` / `Error` / `Content` 階層は [`docs/architecture.md`](architecture.md) の方針に従い `sealed class` とする
- 親型に共通の状態・実装・constructor 制約を持たせる場合や、class としての継承関係が必要な場合は `sealed class` で表す

## Navigation

- 画面遷移は Navigation Compose に寄せる
- 画面追加時は route 名、引数、戻る挙動を明確にする

## Coroutines / State

- 非同期処理は Coroutines を使い、公開する suspend API を main-safe にする
- ファイル操作など blocking I/O を行う実装が、注入した dispatcher への切り替えを所有する
- `CancellationException` は再送出し、広い例外捕捉で coroutine のキャンセルを握りつぶさない
- `StateFlow`・永続化・メモ画像の構造境界は [`docs/architecture.md`](architecture.md) を、テーブル構成とクエリの制約は [`docs/data-model.md`](data-model.md) を正本とする

## UI Event / Error

以下は新規実装と、event delivery を実質的に変更するコードへ適用します。
既存の Channel event を変更する場合は用途を確認し、重要な処理結果を扱っていれば変更範囲で UI state へ移行します。

- 保存成否やユーザーの確認が必要なエラーなど、失ってはいけない処理結果は UI state に保持し、UI から確認済み callback を受けて消費済みにする
- UI 操作を起点とする画面遷移や認証要求は UI callback として Navigation / UI helper へ渡す
- Channel event は、collector 不在、再生成、送信失敗で失われても処理結果や整合性に影響しない best-effort の通知に限る
- 一回限り event の Channel は、`latest wins`（最新だけ届けばよい）が成立する場合に限り `Channel.CONFLATED` を使う
- `Channel.BUFFERED` は一時的な backpressure を吸収する手段であり、取りこぼしのない永続的な配送手段として扱わない

## Test

- JVM Unit Test（`app/src/test`）の方針は [`docs/unit-test.md`](unit-test.md) を確認する
- Instrumented Test / Compose UI Test（`app/src/androidTest`）の方針は [`docs/instrumented-test.md`](instrumented-test.md) を確認する

## Localization

- 表示文字列は `strings.xml` に寄せる
- デフォルトの `app/src/main/res/values/strings.xml` が日本語、英語リソースは `app/src/main/res/values-en/strings.xml` に配置している。表示文言を追加・変更するときは双方を更新する
- UI 実装時は長い英語文字列でも崩れない余白を確保する
