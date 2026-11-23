# migration

`LiteMemoDatabase` の version を上げ、`LiteMemoMigrations.ALL` に migration を追加する。

## 確認する対象

- `data/local/LiteMemoDatabase`（version）、`data/local/migration/`、`data/di/DatabaseModule`
- 変更前後の `app/schemas/` JSON
- `app/src/androidTest/kotlin/com/lambdarc/litememo/data/local/migration/` の migration test

## 実装時の注意

- DB version を上げたら対応する `Migration` を `LiteMemoMigrations.ALL` へ必ず登録する（登録漏れは起動時 crash になる）。
- 既存データの保持、NOT NULL カラムの `DEFAULT` 値（entity の `@ColumnInfo(defaultValue = ...)` と一致させる）、index、外部キー、論理削除済み行（`deletedAt IS NOT NULL`）の扱いを明示する。
- 破壊的 migration（`fallbackToDestructiveMigration`）に頼らない。

## テスト判断

- `app/src/androidTest/kotlin/com/lambdarc/litememo/data/local/migration/` で `MigrationTestHelper` を使い、旧 schema からの移行、既存データの保持、unique / 外部キー等の制約を検証する（詳細は [`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）。

## 検証観点

- 旧バージョンからの移行で既存データ（ごみ箱内メモ・タグ・画像参照を含む）が失われないか。
- migration 後のスキーマが `app/schemas/` の新 version JSON と一致するか。
