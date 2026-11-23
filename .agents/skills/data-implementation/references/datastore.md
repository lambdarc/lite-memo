# DataStore

軽量な設定値の永続化を DataStore に閉じ、読み取りのフォールバック方針を安全に保つ。
アプリロック設定の要件は [`docs/app-lock.md`](../../../../docs/app-lock.md) を正本とする。

## 確認する対象

- `data/repository/` の DataStore 実装と、対応する `domain/repository/` の設定系 interface
- `di/` の DataStore 定義（`DataStoreModule`）と `data/util/` の例外処理ヘルパー
- 保存する preference key と既定値

## 実装時の注意

- `app_lock_enabled` だけ `IOException` 時に `true` へ倒すフェイルクローズ（`enabledOnIoError()`）になっており、一般設定の既定値フォールバック（`dataOrEmptyOnIoError()`）とは意図的に非対称である。安易に共通化しない。
- いずれの経路も捕捉対象は `IOException` に限定し、それ以外の例外（`CancellationException` を含む）は再送出する。
- enum 名を文字列で保存する場合は、未知の文字列が保存されていても例外を投げず既定値へフォールバックさせる。
- 構造化データは Room に寄せ、DataStore は軽量な設定値に限定する（[`docs/architecture.md`](../../../../docs/architecture.md)）。

## テスト判断

- 未設定時の既定値、書き込み反映、未知の文字列値からのフォールバック、`IOException` 時の一般設定 / アプリロックの挙動、および `IOException` 以外の例外再送出を JVM Unit Test（`runTest`）で検証する。

## 検証観点

- 設定追加・変更時に既定値と後方互換（未知値・未設定キー）が保たれているか。
- アプリロックのフェイルクローズ挙動が一般設定のエラー処理に引きずられて緩んでいないか。
