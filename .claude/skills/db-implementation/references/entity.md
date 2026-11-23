# entity

Room entity を `data/local/entity/` に定義し、schema export と整合させる。
テーブル構成と制約の前提は [`docs/data-model.md`](../../../../docs/data-model.md) を正本とする。

## 確認する対象

- `data/local/entity/` 配下の既存 entity と `data/local/LiteMemoDatabase`
- `data/local/model/` の query result model、対応する `data/mapper/`、および `app/schemas/` の JSON

## 実装時の注意

- カラム追加・変更は null 許容、既定値、index、外部キー（`ON DELETE CASCADE` 等）を明確にし、mapper / migration / schema export と一体で計画する。
- 既存テーブルへ `ALTER TABLE ... ADD COLUMN` で NOT NULL カラムを追加する場合は SQLite の制約で `DEFAULT` が必要になる。entity 側に `@ColumnInfo(defaultValue = ...)` を書く場合は migration SQL の `DEFAULT` 値と一致させる（不一致だと `MigrationTestHelper` や実行時の schema 検証で失敗する）。テーブルを作り直して旧行をコピーする migration では、`DEFAULT` を設けずに値を埋めてもよい。
- 新規 entity を追加する場合は `LiteMemoDatabase` の `@Database(entities = [...])` へ登録する。
- DB 詳細を domain / UI に漏らさない。

## テスト判断

- entity 変更に伴う DAO test・migration instrumented test（[`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）と mapper Unit Test の要否を判断する。

## 検証観点

- `app/schemas/` の差分が entity 変更と一致するか。
- 既存データの後方互換と制約（unique / 外部キー / default 値）が保てるか。
