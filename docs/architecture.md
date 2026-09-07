# Architecture

Lite Memo は Clean Architecture をベースに、UI 層は MVVM で構成します。
実装時は過剰に層を増やさず、機能が増えた時に自然に拡張できる粒度を優先します。

## 基本方針

- `ui` は `data` の実装詳細に依存せず、必要なビジネスルールと抽象を `domain` 経由で利用する
- `data` は `domain` の Repository interface / provider を実装し、Room、DataStore、外部 SDK などのデータ源を扱う
- Domain 層は Android Framework に依存しない
- UseCase は UI entry point から domain の操作へ到達する唯一の窓口とし、単純委譲であっても境界として置く
- Android UI と密接な SDK や OS API は UI / app entry 側に閉じ、データ源に関わる Android 依存は domain の抽象を data が実装する
- 依存注入は Hilt で行い、`LiteMemoApplication` と app 直下の `di` / `data.di` を composition boundary とする。app 直下の `di` はアプリ全体の binding、`data.di` は data 層の binding を担う
- Glance ウィジェットのように `@AndroidEntryPoint` を使えない UI entry point は、`ui.widget.di` の `@EntryPoint` から `SingletonComponent` の依存を取得する

## レイヤー構成

プレゼンテーション層は `ui` パッケージとして実装します。機能コードの依存方向は `ui -> domain <- data` を守ります。app entry と DI module は依存を組み立てる composition root のため、配線に必要な具象型を参照できます。

以下は主な既存パッケージの代表です。網羅的な固定一覧ではなく、新しいファイルは責務が最も近い既存パッケージへ追加します。`ui` は機能別に分けず、主要な役割別パッケージを保ちます。型の接尾語だけを根拠に `data` / `type` / `event` などの補助パッケージを増やしません。

- `ui`: Compose 画面、ViewModel、UI state
  - `screen`: state と callback を受け取る `XxxScreen`。メニュー開閉など短命な UI element state は保持してよい
  - `route`: ViewModel と画面を接続し、Android 画面では `collectAsStateWithLifecycle()` で state を収集する `XxxRoute`
  - `viewmodel`: 画面ごとの `XxxViewModel`
  - `state`: UI state（`XxxUiState`）とUI処理結果（`XxxUiResult`）
  - `model`: 表示用モデル（`XxxUiModel`）
  - `auth`: 認証など Android Framework と連携する UI ヘルパー（例: `AppLockAuthenticator`）
  - `util`: 画面から呼ぶユーティリティ（例: `launchShareMemo`）
  - `component`: 再利用する Compose 部品
  - `navigation`: 画面遷移先の定義（`LiteMemoDestination`）と NavHost 配線
  - `theme`: Compose / Material 3 テーマ
  - `widget`: ViewModel / Route とは別の UI entry point である Glance ウィジェット。機能内では `common` / `data` / `di` とウィジェット別パッケージに分けてよい

  画面固有の callback 集約、event、補助 data class、enum、test tag は、
  所有する `screen` / `route` / `viewmodel` / `state` / `model` / `component` へ置きます。
  UI state / result / event は、それぞれ `XxxUiState` / `XxxUiResult` / `XxxUiEvent` と命名します。
  複数箇所から参照する契約は主要な役割パッケージ内で独立ファイルにし、
  所有者だけが使う小型型は所有者ファイルへまとめます。

- `domain`: Android Framework に依存しないビジネスロジック
  - `model` / `model/value`: ドメインモデルと値オブジェクト
  - `usecase`: ビジネス上の操作を表す UseCase
  - `repository`: Repository interface
  - `provider`: 時刻・ID 生成など外部依存の抽象（例: `CurrentTimeProvider`、`MemoIdProvider`）
  - `exception`: domain rule の違反を表す例外
- `data`: domain の interface を実装し、データ源を扱う
  - `local`: Room database と `entity` / `dao` / `migration` / query result 用 `model`
  - `repository`: Repository 実装（例: `RoomMemoRepository`、`DataStoreUserSettingsRepository`）
  - `mapper`: data model ↔ domain model 変換
  - `model`: DTO など data 層固有のモデル
  - `image`: メモ画像ファイルの入出力
  - `provider`: domain provider の Android / runtime 実装
  - `util`: data 層内で共有する補助処理
  - `di`: database、DAO、Repository、provider、dispatcher の Hilt module
  - `export`: Export / Import の入出力
- app 直下
  - `LiteMemoApplication` / `MainActivity`: アプリと Android 画面の entry point
  - `di`: scope、設定、serialization、DataStore などアプリ全体の Hilt module

## MVVM

- ViewModel は画面全体の状態とビジネス処理に由来する state を `StateFlow` で公開する
- Android の Route は `StateFlow` を `collectAsStateWithLifecycle()` で収集し、state と callback を Screen に渡す
- プロセス再生成後に復元する最小限の状態は、ViewModel 側では `SavedStateHandle`、UI 側では `rememberSaveable` を使う
- メニューの開閉、focus など再生成後の復元が不要な UI element state は、必要な Composable の近くで `remember` により保持する
- Glance は Android Compose の Route / Lifecycle とは実行モデルが異なるため、ウィジェット固有の state 更新方法を使う
- ViewModel に Android View や Context 依存を直接持ち込まない
- 画面固有の状態とドメインモデルを混ぜすぎない
- 新規実装または event delivery を実質的に変更する場合、保存結果など失ってはいけない処理結果は UI state に保持し、UI から確認済み callback を受けて消費済みにする
- UI 操作だけで完結する画面遷移は UI callback として Navigation へ渡し、ViewModel の Channel を必須経路にしない

## UseCase

- 画面から直接 Repository implementation を呼ばない
- ViewModel や Glance などの UI entry point は常に UseCase に依存し、domain の Repository interface / provider へ直接依存しない
- UseCase は、できるだけ1つの明確な操作を表す
- ビジネスルールも調停も持たない単純委譲でも、UI と domain の依存を切る境界として UseCase を置く
- 認証、Navigation、Activity Result など UI と密接な Android API は UI / app entry 側で扱い、結果だけを ViewModel や domain の操作へ渡す

## Value Object

- Domain の意味を持つ値は、必要に応じて値オブジェクトで表す
- 例: `MemoTitle`、`MemoId`、`TagName`、`TagColor`、`TimestampMillis` など
- 値オブジェクトは生成時に制約を満たすようにし、不正な状態を後段へ流さない
- Room entity や DTO へ変換するときは、data 層で mapper を使う

## Data

- Room はメモ・タグ・メモ画像メタデータなどの構造化データの source of truth とする。スキーマは `app/schemas/` にエクスポートし、変更時は migration を追加する
- DataStore はテーマ、表示設定などの軽量な設定値を扱う
- メモ画像ファイルはアプリ専用領域に保存し、Room には画像 ID・ファイル名などの参照情報を保持する
- ZIP export/import は manifest のメモ・タグ・画像metadataと画像ファイル本体を対象とする
- 時刻・ID 生成やデータ源に関わる外部サービスは domain の provider / repository interface 経由で扱い、実装は data 層に閉じる。UI と密接な SDK は前述の UI / app entry 方針に従う
- data 層の model と domain model がずれる場合は mapper を置く

## メモ画像

- UI は画像ファイルの実パスを直接組み立てず、`ResolveMemoImagePathUseCase` 経由で解決する
- 画像添付の URI 取得は Activity Result を扱う `ui/route` に置き、ContentResolver による読み書きは data 層に閉じる。ViewModel は Android 非依存の文字列として受け取り、UseCase 経由で扱う
- 画像追加はアプリ専用領域への copy 成功後に Room の参照を保存し、存在しないファイルを DB から参照させない
- 参照を削除するときは、Room transaction 内で参照更新と削除対象の収集を行い、commit 後に対象ファイルを削除する
- commit 後のファイル削除は冪等かつ best-effort にし、削除失敗を理由に Room の参照を復元しない
- 未保存画像は保存処理とは別に cleanup する。現行の後続 cleanup は、中断した import セッションの接頭辞を持つ未参照ファイルだけを回収する。全画像ファイルを走査して Room と突き合わせる汎用の orphan cleanup は実装していない
