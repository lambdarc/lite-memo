# DAO

`data/local/dao/` の DAO で、既存の絞り込み・整合ルールと矛盾しないクエリを提供する。
テーブル構成・論理削除・クエリ制約の仕様は [`docs/data-model.md`](../../../../docs/data-model.md) を正本とする。

## 確認する対象

- `data/local/dao/` 配下の既存 DAO・補助処理と `data/local/model/` の query result model
- [`docs/data-model.md`](../../../../docs/data-model.md)（active / trash / search / calendar / 楽観的ロックの制約）

## 実装時の注意

- `MemoWithRefs` のように `@Relation` を持つモデルを返す `@Query` には必ず `@Transaction` を付ける（複数 SQL 発行間の不整合を防ぐため）。
- 新規 DAO を追加する場合は `LiteMemoDatabase` の abstract メソッドと `data/di/DaoModule` の `@Provides` の双方へ登録する。
- 返す型は Flow 観測系と suspend 実行系を用途で分け、active / trash（`deletedAt` の null 判定）の境界をクエリごとに明示する。
- 検索・カレンダー等の絞り込み条件を DAO と UseCase で二重定義しない。

## テスト判断

- 実 SQLite 上でのクエリ絞り込み・並び順・トランザクション・外部キー制約の検証は instrumented test（`RoomDaoInstrumentedTest`、詳細は [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）で押さえる。
- DAO のデフォルトメソッドが担う chunk 分割や引数検証などは `app/src/test/kotlin/com/lambdarc/litememo/data/local/dao/` の JVM Unit Test で押さえる。

## 検証観点

- 追加クエリが既存 index を活かせているか。
- `deletedAt` の境界条件、半開区間、`IN (...)` の chunk 分割、tie-break 順が [`docs/data-model.md`](../../../../docs/data-model.md) と一致するか。
