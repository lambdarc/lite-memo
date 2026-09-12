# one-shot event

UI event を、保持すべき処理結果、UI callback、損失可能な Channel event に分けて扱う。

## 確認する対象

- 対象 ViewModel の UI state / event Channel と、Route 側の callback / event 収集
- `docs/implementation-guidelines.md` の「UI Event / Error」節（event 表現と Channel 種別の選択規約）

## 実装時の注意

- event 表現と Channel 種別は、適用条件を含めて [`docs/implementation-guidelines.md`](../../../../docs/implementation-guidelines.md#ui-event--error) を正本とする。軽微な保守を理由に、対象外の既存 event の移行へ範囲を広げない。
- event 型は `XxxUiEvent` と命名して公開元の ViewModel と同じ `ui/viewmodel` に置き、接尾語だけの `ui/event` パッケージを作らない。

## テスト判断

- UI state の処理結果は、確認済み callback まで保持されることを検証する。
- Channel event は発行・順序を Turbine などで検証し、collector 不在時の配送保証を前提にしない。

## 検証観点

- Channel event の取りこぼしが処理結果や整合性に影響しないか。
- UI state や UI callback で表すべき事象を Channel event に流していないか。
