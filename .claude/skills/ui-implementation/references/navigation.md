# navigation

画面遷移を Navigation Compose に寄せ、遷移先を `ui/navigation` に定義する。

## 確認する対象

- `ui/navigation/`（`LiteMemoDestination`、`LiteMemoApp` の NavHost）
- 遷移元 / 遷移先の Route と引数

## 実装時の注意

- route 名、引数、戻る挙動を明確にする。
- 遷移先追加時は既存の下部主要導線（`NavigationBar`）と矛盾させない。
- UI 操作を起点とする遷移は UI callback で扱い、失ってはいけない遷移前の処理結果は UI state に持つ（`one-shot-event.md`）。

## テスト判断

- 遷移分岐にロジックがある場合は ViewModel / UI Test の要否を判断する。

## 検証観点

- 戻る / 再遷移で状態が壊れないか。
- 引数の型・必須性が明確か。
