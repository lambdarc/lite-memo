---
name: domain-implementation
description: 入力制約、業務ルール、値の妥当性を変える依頼で使う。Lite Memo の Domain 層の domain model、value object、UseCase、Repository interface、provider を担当する。
---

# 目的

Lite Memo のビジネスルールと公開 contract を、Android Framework に依存しない domain 層へ閉じる。

# 最初に確認するもの

- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`docs/unit-test.md`](../../../docs/unit-test.md)
- [`app/src/main/kotlin/com/lambdarc/litememo/domain/`](../../../app/src/main/kotlin/com/lambdarc/litememo/domain/)
- 関連する [`app/src/test/kotlin/com/lambdarc/litememo/domain/`](../../../app/src/test/kotlin/com/lambdarc/litememo/domain/)

# 役割クラスと参照先

変更対象の役割クラスを見分け、該当する reference だけを読んでから実装する。
薄い役割は reference を作らず docs に委ねる。

| 役割クラス | 参照 |
| --- | --- |
| value object | [`references/value-object.md`](references/value-object.md) |
| UseCase | [`references/usecase.md`](references/usecase.md) |
| provider（時刻・ID 生成などの抽象） | [`references/provider.md`](references/provider.md) |
| Repository interface | reference なし。`docs/architecture.md` の contract 方針に従う |
| 単純な model | reference なし。`docs/architecture.md` の contract 方針に従う |

# 手順

1. 変更対象の役割クラスを判定し、該当 reference を読む。
2. 既存 contract で足りるか、新しい interface / UseCase / provider が必要か決める。
3. reference の観点に沿って実装する。
4. 変更した rule を JVM Unit Test で押さえる。
5. 変更内容に該当する検証をすべて実行する。Kotlin 変更は `./gradlew :app:ktlintCheck :app:detekt :app:testProdDebugUnitTest` で検証する。domain は Android resource を持たないため lint task は通常不要。task 一覧の正本は [`docs/development-setup.md`](../../../docs/development-setup.md)。
6. 変更内容、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- Domain 層に Context、URI、Room、DataStore、Compose、Android resource を持ち込まない。
- Domain contract の変更が UI、Data、DB へ波及するときは、変更対象に対応する Skill を併用する。
- Repository implementation の都合で domain model を歪めない。
- 新しい公開 API は、既存 contract で足りない理由がある場合だけ追加する。
- 例外や require/check の意味が UI 表示と混ざらないようにする。
