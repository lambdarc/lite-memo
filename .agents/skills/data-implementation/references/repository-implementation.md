# Repository 実装

domain の Repository interface をデータ源へ接続し、トランザクション・ファイル整合・例外境界を保つ。
DB 制約や画像整合の仕様は [`docs/data-model.md`](../../../../docs/data-model.md)、ウィジェット連携は [`docs/widget.md`](../../../../docs/widget.md) を正本とする。

## 確認する対象

- `data/repository/` 配下の既存実装と `data/util/` の共有補助処理
- 対応する `domain/repository/` interface と `domain/exception/` の例外型
- 接続先の DAO / DataStore / ファイル・`ContentResolver` の制約

## 実装時の注意

- トランザクション境界を使い分ける。単一 DAO 内の複合処理は DAO の `@Transaction` に任せ、複数 DAO を跨ぐ処理（例: タグとメモを一体で書き込む import）は `database.withTransaction` で囲む。
- Room commit 後の画像ファイル差分削除や、失敗時の cleanup / rollback は `withContext(NonCancellable)` で囲む。`CancellationException` は握り潰さず再送出し、cleanup 側の失敗は主例外を隠さないよう `addSuppressed` でぶら下げる。
- `IN (...)` クエリへ可変長リストを渡すときは、SQLite の変数上限を超えないよう `SQLITE_QUERY_PARAMETER_BATCH_SIZE` で `chunked` する。
- ファイル・`ContentResolver` などの blocking I/O を含む処理は `@IoDispatcher` 上で実行する。
- ウィジェット更新 API（Glance）を Repository から直接呼ばず、Room の観測 Flow を崩さないことで更新連携を保つ。
- 例外変換は UI や上位層で区別が必要な業務例外（例: タグ名重複、編集競合、import 失敗）だけを `domain/exception/` の例外へ変換し、一般的な失敗まで過剰にラップしない。
- data model と domain model の変換は `data/mapper/` に委ね、Repository 実装へ散らさない。

## テスト判断

- 振る舞いと例外境界・cleanup・chunk 分割は JVM Unit Test を基本にする。
- Room の実トランザクションや `ContentResolver`・ファイルシステムを跨ぐ経路は instrumented test で押さえる（[`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）。

## 検証観点

- 失敗・キャンセル時に DB 更新や一時ファイル・画像ファイルが中途半端な状態で残らないか。
- 業務上区別が必要な例外が domain 例外へ変換され、data 由来の型が domain へ漏れていないか。
