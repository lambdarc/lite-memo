# navigation

画面遷移を Navigation Compose に寄せ、遷移先定義・引数エンコード・NavHost 配線を `ui/navigation/` に置く。

## 確認する対象

- `ui/navigation/` 配下の遷移先定義・NavHost 配線・引数エンコーダ・ウィジェット遷移要求
- `MainActivity` のトップレベル分岐（全画面ゲートと `NavHost` の境界）
- 遷移元 / 遷移先の `ui/route/` と引数

## 実装時の注意

- route 名、引数、戻る挙動、下部主要導線（`NavigationBar`）の表示有無を明確にする。
- ルート文字列に埋め込む引数は、URL 特殊文字による経路破損を防ぐため必ず `NavigationArgumentEncoder` を通す。
- 遷移や戻る操作は、`ui/navigation/LiteMemoApp.kt` の NavHost 配線内で private の二重遷移防止ヘルパー（`navigateOnce` / `popBackStackIfResumed` / `navigateWhenResumed`）を使い、連打や非 `RESUMED` 状態での重複遷移・誤 pop を防ぐ。Route 側へは `NavController` を渡さず遷移 callback で渡す。下部主要導線のタブ切替は `saveState` / `restoreState` を伴う `navigate` を直接使い、このヘルパーの対象外とする。
- アプリロックやチュートリアルのような全画面ゲートは `NavHost` のルートに入れず、`MainActivity` 直下で切り替える。ゲート表示中にアプリ本体の back stack や画面状態を保持するため、本体側は `rememberSaveableStateHolder` で包む。
- UI 操作を起点とする遷移は UI callback で扱い、失ってはいけない遷移前の処理結果は UI state / `XxxUiResult` に持つ（[`one-shot-event.md`](one-shot-event.md)）。
- 画面をまたぐ Snackbar は `NavHost` 外側で共有する `SnackbarHostState` へ接続する。

## テスト判断

- 引数エンコードや遷移要求の解析・分岐にロジックがある場合は JVM Unit Test を追加する。
- 画面遷移を含む統合確認が必要な場合は [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md) の方針に従って instrumented test の要否を判断する。

## 検証観点

- 連打・戻る・再遷移・ウィジェット経由の遷移で back stack や状態が壊れないか。
- 引数のエンコード・型・必須性・既定値が遷移元と遷移先で一致しているか。
- 全画面ゲートをまたいでもアプリ本体の状態が失われないか。
