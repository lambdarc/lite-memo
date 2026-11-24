# schema (app/schemas)

Room の schema JSON export を entity / migration と一致させる。

## 確認する対象

- `app/schemas/` 配下（`LiteMemoDatabase` の version 別 JSON）
- `app/build.gradle.kts` の `room` ブロック
- 対応する `data/local/entity/` と `LiteMemoDatabase` の version

## 実装時の注意

- schema JSON は `./gradlew :app:kspDevDebugKotlin :app:copyRoomSchemas` で生成し、手編集しない。差分は entity / migration 変更と同じ commit に含める。
- DB version を上げる際は、過去の schema JSON を変更せず新しい version の JSON を追加する（`MigrationTestHelper` を使う migration test が過去 JSON を参照するため）。
- version 番号と export ファイル名の対応を崩さない。

## テスト判断

- schema 変更に伴う migration instrumented test・DAO test の要否を判断する（[`docs/instrumented-test.md`](../../../../docs/instrumented-test.md)）。

## 検証観点

- 生成・追加された JSON が entity の実体と一致するか。
- 過去の version JSON が意図せず書き換わっていないか、新 version の export 漏れがないか。
