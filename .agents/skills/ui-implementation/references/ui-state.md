# UI state / UI model

画面状態を `XxxUiState`、UI処理結果を `XxxUiResult`（`ui/state`）、表示用モデルを `XxxUiModel`（`ui/model`）に置く。

## 確認する対象

- `ui/state/` の `XxxUiState` / `XxxUiResult` と `ui/model/` の `XxxUiModel`、対応する domain model
- 表示変換を行う Screen / Component 付近の extension

## 実装時の注意

- 画面状態は `ui/state` と ViewModel に寄せ、Compose に状態を持たせすぎない。
- 表示固有の整形や UI 専用情報が必要な場合は `XxxUiModel` へ写し、不要な変換型は増やさない。
- loading / empty / error / content などの分岐を状態として表す。
- 主要 UiState と一緒に使う小型 state / enum は、関連が明確で読みやすい範囲なら同じファイルのトップレベル宣言へまとめる。

## テスト判断

- 状態から表示分岐が決まる箇所は ViewModel test で押さえる。

## 検証観点

- UI が data model / Room entity を直接参照していないか。
- `XxxUiModel` の追加が表示固有の整形・UI 専用情報で正当化できるか（できないなら domain model をそのまま使う）。
