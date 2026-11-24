# viewmodel

画面ごとの `@HiltViewModel` で UI state を公開し、UseCase 経由で domain へアクセスする。

## 確認する対象

- `ui/viewmodel/` の対象 ViewModel と、公開する `ui/state/`
- 使用する `domain/usecase/` と、one-shot event の扱い（[`one-shot-event.md`](one-shot-event.md)）

## 実装時の注意

- 画面状態は原則 `StateFlow` で公開する。上流 Flow を合成して `stateIn` する場合は `SharingStarted.WhileSubscribed(5_000L)` を基本とする。
- domain 層への依存は UseCase に限る（domain の Repository interface / provider へ直接依存しない）。UseCase 以外に受け取ってよい依存は `SavedStateHandle` と、`di/`（`data/di/` を含む）から提供される実行環境の値（`ZoneId`、および Qualifier 付きの dispatcher・アプリ scope・アプリバージョン）のみとする。
- 上流 Flow の失敗は `.catch` で `Error` 状態など state の値へ変換し、選択・フィルタ・検索などの操作状態の観測を止めない。広い例外捕捉や `.catch` 内では `CancellationException` を再送出する（または `currentCoroutineContext().ensureActive()` でキャンセルを維持する）。
- Android View / Context 依存を ViewModel に直接持ち込まない。
- 処理結果や通知の表現は [`one-shot-event.md`](one-shot-event.md) に従う。
- ViewModel 固有の event 契約や `internal` / `private` の共有補助は `ui/viewmodel/` に置き、`event` / `data` パッケージへ分散させない。

## テスト判断

- 状態遷移・分岐・失敗時の state 変換・event 発行を JVM Unit Test で押さえる。`Dispatchers.setMain`・`StandardTestDispatcher`・`runTest` の規約は [`docs/unit-test.md`](../../../../docs/unit-test.md) に従う。

## 検証観点

- 初期値・再収集・再試行で状態が破綻しないか。
- 上流 Flow の失敗やキャンセルが操作状態の観測を壊したり握りつぶしたりしないか。
- domain 操作の失敗時に UI state または UI result が適切に反映されるか。
