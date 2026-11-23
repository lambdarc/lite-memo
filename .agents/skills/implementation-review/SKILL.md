---
name: implementation-review
description: 実装差分をレビューする依頼で使う。Lite Memo の依存方向、責務分離、過剰 API、DB migration、UI state、Unit Test 不足を確認し、docs/review.md の形式で指摘する。
---

# 目的

Lite Memo の差分が既存方針に沿っているかを確認し、マージ前に直すべき問題を具体的に洗い出す。

# 最初に確認するもの

- [`docs/review.md`](../../../docs/review.md)
- 差分と関連コード
- 変更領域に応じて該当する技術文書を確認する:
  - 設計・実装規約に関わる場合: [`docs/architecture.md`](../../../docs/architecture.md) / [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
  - テストに関わる場合: [`docs/unit-test.md`](../../../docs/unit-test.md) / [`docs/instrumented-test.md`](../../../docs/instrumented-test.md)
  - DB・テーブル・クエリに関わる場合: [`docs/data-model.md`](../../../docs/data-model.md)
  - Export / Import に関わる場合: [`docs/export-import-format.md`](../../../docs/export-import-format.md)
  - メモ編集ライフサイクルに関わる場合: [`docs/memo-edit-lifecycle.md`](../../../docs/memo-edit-lifecycle.md)
  - アプリロックに関わる場合: [`docs/app-lock.md`](../../../docs/app-lock.md)
  - ホーム画面ウィジェットに関わる場合: [`docs/widget.md`](../../../docs/widget.md)
  - CI・ワークフローに関わる場合: [`docs/ci.md`](../../../docs/ci.md)

# 手順

1. 比較基準を確定する（判断手順は `docs/review.md` の「比較範囲」を正本とする）。
2. 差分の目的、変更範囲、既存実装との関係を確認し、該当領域の技術文書を読む。
3. `ui -> domain <- data` の依存方向が崩れていないか見る。
4. ViewModel、UseCase、Repository interface、Repository implementation、mapper、DAO の責務が混ざっていないか見る。
5. 新規 API、抽象化、DB schema、画面構成が本当に必要か確認する。
6. 変更した振る舞いに対するテストがあるか、テスト観点が妥当か確認する。
7. 変更した振る舞いが影響する `docs/` や Skill の記述を陳腐化させていないか確認する。
8. 指摘の分類、形式、終了時の報告は `docs/review.md` を正本とする。

# 注意事項

- 好みではなく、バグ、依存方向、責務分離、保守性、テスト不足を優先する。
- テストコードだけの実装・修正は `test-implementation` を優先する。
- 問題がない場合も、残るリスクや未検証項目を明記する。
- DB schema 変更がある場合は migration と schema JSON を必ず確認する。
- UI 変更がある場合は strings（日本語 / 英語）、preview、ライト / ダーク、イベント取りこぼしを確認する。
