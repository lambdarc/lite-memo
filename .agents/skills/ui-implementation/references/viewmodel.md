# viewmodel

画面ごとの ViewModel で UI state を公開し、UseCase または domain の抽象へ依存する。

## 確認する対象

- `ui/viewmodel/` の対象 ViewModel と、公開する `XxxUiState`（StateFlow）
- 使用する UseCase / domain abstraction と、one-shot event の Channel

## 実装時の注意

- UI state は StateFlow で公開し、依存は UseCase に限る。domain の Repository interface / provider へ直接依存しない。
- Android View / Context 依存を ViewModel に直接持ち込まない。
- 失ってはいけない処理結果は UI state に保持して確認済み callback で消費し、失われても影響しない通知だけ Channel event に流す（`one-shot-event.md`）。
- 画面固有の状態とドメインモデルを混ぜすぎない。
- ViewModel 固有の event 契約や private 補助型は `ui/viewmodel` の所有者付近へ置き、`event` / `data` パッケージへ分散させない。

## テスト判断

- 状態遷移・分岐・event 発行を ViewModel test で押さえる。`runTest` と必要な test dispatcher を使う。

## 検証観点

- 初期値・再収集で状態が破綻しないか。
- domain 操作の失敗時に UI state が適切に反映されるか。
