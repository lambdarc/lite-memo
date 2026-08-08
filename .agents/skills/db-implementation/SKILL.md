---
name: db-implementation
description: 保存項目の増減、検索条件、DB のバージョン変更が絡む依頼で使う。Lite Memo の Room entity、DAO、migration、app/schemas を担当する。
---

# 目的

Lite Memo の Room schema と migration を、既存 DB 構成とテストに沿って安全に変更する。

# 最初に確認するもの

- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`docs/unit-test.md`](../../../docs/unit-test.md)
- [`app/src/main/kotlin/com/lambdarc/litememo/data/local/`](../../../app/src/main/kotlin/com/lambdarc/litememo/data/local/)
- [`app/schemas/`](../../../app/schemas/)
- [`app/src/androidTest/kotlin/com/lambdarc/litememo/data/local/`](../../../app/src/androidTest/kotlin/com/lambdarc/litememo/data/local/)

# 役割クラスと参照先

変更対象の役割クラスを見分け、該当する reference を読んでから実装する。
schema を変える場合は entity / DAO / mapper / Repository / migration / schema export を一体で計画し、関連する reference を読む。

| 役割クラス | 参照 |
| --- | --- |
| entity | [`references/entity.md`](references/entity.md) |
| DAO | [`references/dao.md`](references/dao.md) |
| migration | [`references/migration.md`](references/migration.md) |
| schema（`app/schemas/`） | [`references/schema.md`](references/schema.md) |

mapper / Repository への波及は [`data-implementation`](../data-implementation/SKILL.md) を併用する。

# 手順

1. 変更対象の役割クラスを判定し、該当する reference を読む。
2. schema を変える場合は entity / DAO / mapper / Repository / migration / schema export の波及範囲を洗い出す。
3. reference の観点に沿って実装する。
4. migration instrumented test と DAO test の要否を判断し、`app/schemas/` の更新漏れを確認する。
5. 変更内容に該当する検証をすべて実行する。Kotlin 変更と DB 変更は `./gradlew :app:ktlintCheck :app:detekt :app:connectedDevDebugAndroidTest` で検証する（task 一覧の正本は [`docs/development-setup.md`](../../../docs/development-setup.md)）。schema 変更では `./gradlew :app:kspDevDebugKotlin :app:copyRoomSchemas` で schema を export し、`git diff -- app/schemas` で変更対象 version の schema JSON が更新されたことを確認する。schema 差分がなければ未完了とする。
6. 変更内容、実行した task と結果、schema export と schema 差分の確認結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- Room schema 変更時は `app/schemas/` の差分を必ず確認する。
- アプリが未リリースでも、既存テストや schema export と矛盾する変更は避ける。
- DB 詳細を domain や UI に漏らさず、変換は mapper / Repository 実装に閉じる。
- DataStore や export/import の変更だけなら `data-implementation` を優先する。
