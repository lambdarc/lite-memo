---
name: data-implementation
description: 保存、読み込み、ファイル入出力、外部データとの変換が絡む依頼で使う。Lite Memo の Data 層（Repository 実装、mapper、DataStore、export/import、ContentResolver、Hilt module、provider 実装、domain と data の変換境界）の設計、実装、修正を扱う。「エクスポートしたファイルが壊れる」「設定を保存したい / 既定値をどうするか」「画像の URI が読めない」「変換で値が落ちる」のように、Repository や mapper という語が出てこなくても、データの永続化や受け渡しの問題ならこの skill を使う。テーマや表示設定のような軽量な設定値の追加も、保存先は DataStore なのでここが担当。Room の schema や migration を伴う場合は db-implementation も併用し、テストコードだけなら test-implementation を優先する。
---

# 目的

Lite Memo の data 層で、domain の interface を既存データ源に接続し、実装詳細を UI / domain へ漏らさない。

# 最初に確認するもの

- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`docs/unit-test.md`](../../../docs/unit-test.md)
- [`app/src/main/kotlin/com/appvoyager/litememo/data/`](../../../app/src/main/kotlin/com/appvoyager/litememo/data/)
- 関連する [`app/src/test/kotlin/com/appvoyager/litememo/data/`](../../../app/src/test/kotlin/com/appvoyager/litememo/data/) と [`app/src/androidTest/kotlin/com/appvoyager/litememo/data/`](../../../app/src/androidTest/kotlin/com/appvoyager/litememo/data/)

# 役割クラスと参照先

変更対象の役割クラスを見分け、該当する reference を読んでから実装する。
境界を跨ぐ変更（例: Repository 実装や export / import が mapper・DataStore・ContentResolver に波及する）では、関連する reference を複数読む。
薄い役割は reference を作らず docs に委ねる。

| 役割クラス | 参照 |
| --- | --- |
| Repository 実装 | [`references/repository-implementation.md`](references/repository-implementation.md) |
| mapper | [`references/mapper.md`](references/mapper.md) |
| DataStore | [`references/datastore.md`](references/datastore.md) |
| export / import | [`references/export-import.md`](references/export-import.md) |
| ContentResolver / URI | [`references/content-resolver.md`](references/content-resolver.md) |
| provider 実装 / Hilt module の単純な binding / 単純 DTO | reference なし。`docs/architecture.md` の Data 方針に従う（provider 抽象は domain-implementation） |

# 手順

1. 変更対象の役割クラスを判定し、該当する reference（複数に跨る場合は関連するものすべて）を読む。
2. domain interface の期待値と data source の制約を分けて読む。
3. reference の観点に沿って実装し、変換は mapper、Android 依存は data 層 / app entry に閉じる。
4. 複数の役割クラスに跨る場合は、mapper・data source・domain interface の整合を確認する。
5. 振る舞いを JVM Unit Test または androidTest で押さえる。
6. 変更内容に該当する検証をすべて実行する。Kotlin 変更は `./gradlew :app:ktlintCheck :app:detekt :app:testProdDebugUnitTest`、Room / DataStore / ContentResolver を跨ぐ変更は `./gradlew :app:connectedDevDebugAndroidTest` で検証し、複数に該当するときは各 task を組み合わせる。task 一覧の正本は [`docs/development-setup.md`](../../../docs/development-setup.md)。
7. 変更内容、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- data 層から UI state や Compose model を参照しない。
- domain model の不変条件を壊す値を mapper から流さない。
- Room schema 変更を伴う場合は `db-implementation` も併用する。
- DataStore は軽量な設定用とし、構造化データは Room 側に寄せる。
