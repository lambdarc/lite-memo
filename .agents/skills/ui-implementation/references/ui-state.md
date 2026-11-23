# UI state / UI model

画面状態を `XxxUiState`、UI 処理結果を `XxxUiResult`（`ui/state/`）、表示用モデルを `XxxUiModel`（`ui/model/`）に置く。

## 確認する対象

- `ui/state/` の対象 state / result と既存の共有状態型、`ui/model/` の表示用モデル
- 対応する `domain/model/` と表示変換を行う周辺コード
- 状態設計の正本である [`docs/architecture.md`](../../../../docs/architecture.md#mvvm)

## 実装時の注意

- 空の一覧や新規の空入力は独立した `Empty` 状態にせず、`Content`（または `ScreenUiStatus.CONTENT`）に含める。
- 状態の型は画面の性質で使い分ける（正本は [`docs/architecture.md`](../../../../docs/architecture.md#mvvm)）。
  - 読み込みと失敗がある一覧系画面: `Loading` / `Error` / `Content` の `sealed class` で排他的に表し、表示データは `Content` のみ、エラーをまたいで残す操作状態は `Error` と `Content` の両方に持たせる。
  - 読み込み中も下書きやセッション状態を残すフォーム系画面: `data class` + `ScreenUiStatus`（`LOADING` / `ERROR` / `CONTENT`）で表す。
  - 読み込み・失敗の排他分岐を要しない設定画面やゲート画面: 単一の `data class` で表す。
- 画面状態は `ui/state/` と ViewModel に寄せ、Compose に状態を持たせすぎない。
- 表示固有の整形や UI 専用情報が必要な場合だけ `ui/model/` へ写し、不要な変換型を増やさない。
- 主要 UiState と一緒に使う小型 state / enum は、関連が明確な範囲で同じファイルのトップレベル宣言へまとめてよい。

## テスト判断

- 状態から表示分岐が決まる箇所は ViewModel test で押さえる。
- 状態クラス自体が遷移ロジックや不変条件を持つ場合は、`app/src/test/.../ui/state/` に状態クラス単体の JVM Unit Test を追加する。

## 検証観点

- UI が data model / Room entity を直接参照していないか。
- 独立した状態軸（検索エラー、タグ読込エラー、選択、変更有無など）を無理に 1 つへ統合していないか。
- `XxxUiModel` の追加が表示固有の整形・UI 専用情報で正当化できるか（不要なら domain model をそのまま使う）。
