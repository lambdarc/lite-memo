# screen / component

state と callback を受け取る `XxxScreen`（`ui/screen/`）、ViewModel や Android 副作用と接続する `XxxRoute`（`ui/route/`）、再利用部品（`ui/component/`）を扱う。

## 確認する対象

- `ui/route/` の `XxxRoute` と `ui/screen/` の `XxxScreen` ペア、対応する `ui/state/`
- `ui/component/` の既存再利用部品と test tag

## 実装時の注意

- `XxxRoute` で ViewModel の `StateFlow` を `collectAsStateWithLifecycle()` で収集し、state と callback を `XxxScreen` へ渡す。one-shot event の収集や Snackbar・ActivityResult・共有・`BackHandler`・`LifecycleEventEffect` 等の副作用は Route 側で消費し、Screen へ持ち込まない。
- `XxxScreen` は state と callback を引数で受ける。メニュー開閉など短命な UI element state は必要な Composable の近くで持ってよい。
- ダイアログ等のオーバーレイは、`Loading` / `Error` / `Content` のうち `Content` 分岐でのみ描画する。
- `NavHost` 配下の各画面で内側 `Scaffold` を置く場合は、外側の `Scaffold` とインセットが二重にならないよう `contentWindowInsets = WindowInsets(0, 0, 0, 0)` を指定する。
- `MainActivity` 直下で切り替える全画面ゲート（Route を持たない画面）や、ViewModel を持たない静的画面の Route のように、責務に応じた構成例外がある。
- 画面固有の action、test tag、小型の補助型は所有する `screen` / `component` へ置き、接尾語だけの補助パッケージを作らない。
- 固定色・ライト / ダーク・Preview・Localization の基本方針は [`docs/implementation-guidelines.md`](../../../../docs/implementation-guidelines.md) に従う（Preview は `ui/theme/` のテーマでラップする）。

## テスト判断

- 表示分岐・操作結果を確認する Compose UI Test の要否を判断する（[`compose-ui-test.md`](compose-ui-test.md)）。

## 検証観点

- 読み込み・空状態（`Content` 内）・エラー・通常の表示分岐とオーバーレイの開閉が破綻しないか。
- 長い英語文字列でも崩れない余白と、ライト / ダーク両対応が保たれているか。
