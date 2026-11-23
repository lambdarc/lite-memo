---
name: domain-implementation
description: 入力制約、業務ルール、値の妥当性を変える依頼で使う。Lite Memo の Domain 層の domain model、value object、UseCase、Repository interface、provider、domain exception を担当する。
---

# 目的

Lite Memo のビジネスルールと公開 contract を、Android Framework に依存しない domain 層へ閉じる。

# 最初に確認するもの

- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`app/src/main/kotlin/com/lambdarc/litememo/domain/`](../../../app/src/main/kotlin/com/lambdarc/litememo/domain/)（変更対象周辺のコード）
- テストを追加・修正する場合は [`docs/unit-test.md`](../../../docs/unit-test.md) と関連する [`app/src/test/kotlin/com/lambdarc/litememo/domain/`](../../../app/src/test/kotlin/com/lambdarc/litememo/domain/)
- データモデル・論理削除・クエリ規約に関わる場合は [`docs/data-model.md`](../../../docs/data-model.md)
- Export / Import に関わる場合は [`docs/export-import-format.md`](../../../docs/export-import-format.md)
- メモ編集の保存・競合・破棄に関わる場合は [`docs/memo-edit-lifecycle.md`](../../../docs/memo-edit-lifecycle.md)
- アプリロックに関わる場合は [`docs/app-lock.md`](../../../docs/app-lock.md)

# 役割クラスと参照先

変更対象の役割クラスを見分け、該当する reference だけを読んでから実装する。
薄い役割は reference を作らず docs に委ねる。

| 役割クラス | 参照 |
| --- | --- |
| value object | [`references/value-object.md`](references/value-object.md) |
| UseCase | [`references/usecase.md`](references/usecase.md) |
| provider（時刻・ID 生成などの抽象） | [`references/provider.md`](references/provider.md) |
| Repository interface | reference なし。`docs/architecture.md` の「基本方針」と「レイヤー構成」の domain に従う |
| domain model（`domain/model/`） | reference なし。`docs/architecture.md` の「基本方針」と「レイヤー構成」の domain に従う（value class の検証・正規化は value object 行を参照） |
| domain exception（`domain/exception/`） | reference なし。業務ルール違反・競合・検証失敗など呼び出し側が区別すべき失敗を表す（使い分けは UseCase 行を参照） |

# 手順

1. 変更対象の役割クラスを判定し、該当 reference を読む。
2. 既存 contract で足りるか、新しい interface / UseCase / provider / exception が必要か決める。
3. reference の観点に沿って実装する。
4. 変更した rule を JVM Unit Test で押さえる。
5. 変更した振る舞いと影響範囲に応じて検証を選ぶ。選択方針と実行コマンドは [`docs/development-setup.md`](../../../docs/development-setup.md) の「ローカルでの検証」に従う。
6. 変更内容、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- Domain 層に Context、`android.net.Uri`、Room、DataStore、Compose、Android resource を持ち込まない（URI 書式検証には標準の `java.net.URI` を使う）。
- Domain contract の変更が UI、Data、DB へ波及するときは、変更対象に対応する Skill を併用する。
- Repository implementation の都合で domain model を歪めない。
- 新しい公開 API は、既存 contract で足りない理由がある場合だけ追加する。
- 例外や `require` の意味が UI 表示文言と混ざらないようにする。
