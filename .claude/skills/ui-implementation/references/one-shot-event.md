# one-shot event

UI event を、保持すべき処理結果（`XxxUiResult` / UI state）、UI callback、損失可能な Channel event に分けて扱う。

## 確認する対象

- `ui/viewmodel/` の対象 ViewModel（UI state / `uiResults` / event Channel）と `ui/state/` の `XxxUiResult`
- `ui/route/` の callback / event 収集処理
- [`docs/implementation-guidelines.md`](../../../../docs/implementation-guidelines.md#ui-event--error)（event 表現と Channel 種別の正本）

## 実装時の注意

- event 表現と Channel 種別は、適用条件を含めて [`docs/implementation-guidelines.md`](../../../../docs/implementation-guidelines.md#ui-event--error) を正本とする。軽微な保守を理由に、対象外の既存 event の移行へ範囲を広げない。
- 失ってはいけない処理結果（保存・削除・画像添付などの成否やエラー）は `ui/state/` に `XxxUiResult` を定義して保持し、UI から確認済み callback を受けて消費済みにする。
- 画面本体と処理結果で再コンポーズを分離したい場合は、本体 `uiState` に混ぜず独立した `uiResults: StateFlow<List<XxxUiResult>>` として公開し、Route 側で先頭から順に処理して確認済み callback で ID 指定消費する。
- Channel を使う event 型は `XxxUiEvent` と命名して公開元の ViewModel と同じ `ui/viewmodel/` に置き、接尾語だけの `ui/event` パッケージを作らない（Navigation 境界に属する要求型は `ui/navigation/` に置く）。

## テスト判断

- UI state / `uiResults` の処理結果は、確認済み callback まで保持され、消費後に取り除かれることを検証する。
- Channel event は発行・順序を Turbine などで検証し、collector 不在時の配送保証を前提にしない。

## 検証観点

- Channel event の取りこぼしが処理結果や整合性に影響しないか。
- UI state や UI callback で表すべき事象を Channel event に流していないか。
- `uiResults` の消費漏れや重複消費が起きないか。
