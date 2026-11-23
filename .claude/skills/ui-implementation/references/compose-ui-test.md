# Compose UI Test

画面の表示分岐・ユーザー操作の結果を `app/src/androidTest/` で確認する。

## 確認する対象

- [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)（実行基盤・命名・Compose UI Test 細部・fixture・E2E の正本）
- `app/src/androidTest/kotlin/com/lambdarc/litememo/ui/` の既存テストと fixture
- 対象 `ui/screen/` / `ui/component/` の文字列リソースと test tag

## 実装時の注意

- 細かい見た目より、状態遷移・表示分岐・操作結果を優先して検証する。
- 要素の特定はテスト側への文字列直書きを避け、`R.string.*` 経由のテキスト / `contentDescription` と semantics matcher を基本とする。
- テキストや semantics だけでは一意に識別しにくい要素（動的リスト項目、複数ある同種ボタン、コンテナ領域など）に test tag を付ける。
- test tag は対象の `ui/screen/` または `ui/component/` に置き、`testtag` 専用パッケージを作らない。
- ViewModel ロジックで検証できるものは JVM Unit Test 側に寄せ、UI Test を過剰に増やさない。
- `createComposeRule` の選定、fixture 構成、Screen ヘルパー、Route / E2E テストなどの細部は [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md) に従う。

## テスト判断

- Compose UI に固有の表示分岐・操作結果・Route 副作用だけを instrumented test に置く。

## 検証観点

- 読み込み・空状態（`Content` 内）・エラー・通常の表示分岐が確認できるか。
- 主要操作の結果が callback や画面状態に反映されるか。
