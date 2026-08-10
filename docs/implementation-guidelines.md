# Implementation Guidelines

この文書は実装時の基本方針をまとめます。
詳細設計は、必要になった時点で別文書またはコード上で明確にしてください。

## UI

- UI は Jetpack Compose と Material 3 を基本にする
- 下部主要導線は Material 3 の `NavigationBar` を使う
- Compose Preview を壊さないように、UI コンポーネントは小さく分ける
- ライト / ダーク両対応を前提にし、固定色の直書きを避ける

## Architecture

- 構造は Clean Architecture + MVVM を基本にする
- DI は Hilt を使う
- 依存方向、レイヤーの責務、パッケージ構成、UseCase の粒度は [`docs/architecture.md`](architecture.md) を正本とする

## Kotlin

- 関数名は英語にする
- Route、Screen、ViewModel、主要な UiState / UiModel は、役割と同名の独立ファイルを基本とする
- UI state / result / event は、それぞれ `XxxUiState` / `XxxUiResult` / `XxxUiEvent` と命名する。補助enumも `AppLockUiStatus` のようにUI契約であることを名前に含める
- 同じ責務を構成する小型の class / interface / object / enum は、ファイルが読みやすい範囲で関連する所有者ファイルへトップレベル宣言としてまとめてよい。300行程度を分割検討の目安とするが、機械的な上限にはしない
- sealed hierarchy の直接の子型や、親に強く従属する private 実装型は親型へネストしてよい。外側のインスタンス参照が意図的に必要な場合を除き `inner` は使わない
- `companion object` と匿名 object 式は許容し、`app/src/test` と `app/src/androidTest` のテストコードは上記のファイル配置判断の対象外とする
- `ui` 配下は機能別ではなく主要な役割で分ける。型の接尾語だけを根拠に `action` / `data` / `event` / `testtag` / `type` パッケージを作らず、所有する `screen` / `route` / `viewmodel` / `state` / `model` / `component` へ置く
- ガード条件は早期リターンで扱い、ネストを深くしない
- 意味のある値は primitive のまま広げず、必要に応じて値オブジェクトにする（設計方針は [`docs/architecture.md`](architecture.md) の Value Object を正本とする）
- 取り得る種類だけを閉じ、実装型に別の superclass や複数 interface の実装余地を残す場合は `sealed interface` で表す
- 親型に共通の状態・実装・constructor 制約を持たせる場合や、class としての継承関係が必要な場合は `sealed class` で表す

## Navigation

- 画面遷移は Navigation Compose に寄せる
- 画面追加時は route 名、引数、戻る挙動を明確にする

## State / Data

- ViewModel から UI へは StateFlow を中心に状態を公開する
- 非同期処理は Coroutines を使い、公開する suspend API を main-safe にする
- ファイル操作など blocking I/O を行う実装が、注入した dispatcher への切り替えを所有する
- `CancellationException` は再送出し、広い例外捕捉で coroutine のキャンセルを握りつぶさない
- 永続化の使い分けは [`docs/architecture.md`](architecture.md) の Data 方針を、
  テーブル構成とクエリの制約は [`docs/data-model.md`](data-model.md) を正本とする
- 画像添付などの Android 依存 URI / ContentResolver は ViewModel に直接持ち込まない。配置は [`docs/architecture.md`](architecture.md) の メモ画像 方針を正本とする

## UI Event / Error

以下は新規実装と、event delivery を実質的に変更するコードへ適用します。
既存の Channel event を変更する場合は用途を確認し、重要な処理結果を扱っていれば変更範囲で UI state へ移行します。

- 保存成否やユーザーの確認が必要なエラーなど、失ってはいけない処理結果は UI state に保持し、UI から確認済み callback を受けて消費済みにする
- UI 操作を起点とする画面遷移や認証要求は UI callback として Navigation / UI helper へ渡す
- Channel event は、collector 不在、再生成、送信失敗で失われても処理結果や整合性に影響しない best-effort の通知に限る
- 一回限り event の Channel は、`latest wins`（最新だけ届けばよい）が成立する場合に限り `Channel.CONFLATED` を使う
- `Channel.BUFFERED` は一時的な backpressure を吸収する手段であり、取りこぼしのない永続的な配送手段として扱わない

## Test

- Unit Test の方針は [`docs/unit-test.md`](unit-test.md) を確認する

## Localization

- 表示文字列は `strings.xml` に寄せる
- 日本語 / 英語対応を前提にする
- UI 実装時は長い英語文字列でも崩れない余白を確保する
