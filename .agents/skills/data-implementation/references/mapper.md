# mapper

data model ↔ domain model の変換責務を `data/mapper/` に閉じる。

## 確認する対象

- `data/mapper/` 配下の既存 mapper（トップレベル拡張関数で統一されている）
- 変換元の `data/local/entity/`、`data/local/model/`（query result model）、`data/model/`（DTO）と、変換先の `domain/model/`・`domain/model/value/`

## 実装時の注意

- mapper はトップレベル拡張関数として定義し、変換ロジックを Repository 実装や DAO に散らさない。
- domain model の不変条件を壊す値を流さない（value object の生成制約や参照整合性を尊重する）。
- ファイル I/O やチェックサム計算、ID / ファイル名採番などの副作用を mapper に持ち込まず、呼び出し側で計算済みの値を受け取って組み立てる。
- `MemoExportDto.toDomain()` は常に `deletedAt = null` とする（import したメモをごみ箱ではなく通常のメモとして復元するため）。
- null・既定値・欠損フィールド・参照の並び順（`position`）の扱いを明示する。

## テスト判断

- 代表値・境界・欠損・並び順を含む変換往復を JVM Unit Test で押さえる。

## 検証観点

- entity・query result model・DTO の変更時に変換漏れがないか。
- 双方向変換で順序や必須フィールドが失われていないか。
