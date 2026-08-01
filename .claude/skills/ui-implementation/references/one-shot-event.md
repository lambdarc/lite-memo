# one-shot event

UI event を、保持すべき処理結果、UI callback、損失可能な Channel event に分けて扱う。

## 確認する対象

- 対象 ViewModel の UI state / event Channel と、Route 側の callback / event 収集
- `docs/implementation-guidelines.md` の「UI Event / Error」節（event 表現と Channel 種別の選択規約）

## 実装時の注意

- 保存成否など失ってはいけない処理結果は UI state に保持し、UI からの確認済み callback で消費する。
- UI 操作を起点とする画面遷移や認証要求は UI callback として Navigation / UI helper へ渡す。
- Channel event は、collector 不在や再生成で失われても処理結果や整合性に影響しない best-effort 通知に限る。
- Channel 種別（`CONFLATED` / `BUFFERED` など）は docs の規約に従って選び、`BUFFERED` も取りこぼしのない配送手段として扱わない。
- event 型は `XxxUiEvent` と命名して公開元の ViewModel と同じ `ui/viewmodel` に置き、接尾語だけの `ui/event` パッケージを作らない。

## テスト判断

- UI state の処理結果は、確認済み callback まで保持されることを検証する。
- Channel event は発行・順序を Turbine などで検証し、collector 不在時の配送保証を前提にしない。

## 検証観点

- Channel event の取りこぼしが処理結果や整合性に影響しないか。
- UI state や UI callback で表すべき事象を Channel event に流していないか。
