# Repository 実装

domain の Repository interface を既存データ源に接続し、実装詳細を UI / domain へ漏らさない。

## 確認する対象

- `data/repository/` の既存実装（`RoomMemoRepository`、`RoomTagRepository`、`RoomMemoImportRepository`、`DataStoreUserSettingsRepository`、`FileSystemMemoExportArchiveRepository`、`StagingMemoImportArchiveRepository`、`FileSystemMemoImageStore`）
- 対応する domain interface の期待値と、DAO / DataStore / file / ContentResolver の制約

## 実装時の注意

- interface が期待する不変条件と、data source 側の制約を分けて扱う。
- transaction、重複、順序、失敗時の部分更新の扱いを実装側で明確にする。
- data model と domain model がずれる場合は変換を mapper に委ね、実装本体へ散らさない。

## テスト判断

- 振る舞いは JVM Unit Test を基本にし、Room / ContentResolver / file を跨ぐ経路は androidTest を検討する。

## 検証観点

- domain へ data 由来の型・例外が漏れていないか。
- 失敗時にデータが中途半端に更新されないか。
