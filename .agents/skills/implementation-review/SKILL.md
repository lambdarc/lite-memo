---
name: implementation-review
description: 実装差分を Lite Memo のアーキテクチャ方針に照らして見る依頼で使う。依存方向、責務分離、過剰 API、DB migration、UI state、data mapper、Unit Test 不足を確認し、指摘を docs/review.md の分類と形式で出す。「差分レビューして」「マージして大丈夫か」「責務分離が崩れてないか見て」のようにレビューを求められたら、対象が Lite Memo のコードである限りこの skill を使う。GitHub 上の PR コメントへの対応は address-review、脆弱性中心の確認は security-review、可読性の整理は simplify が主担当。
---

# 目的

Lite Memo の差分が既存方針に沿っているかを確認し、マージ前に直すべき問題を具体的に洗い出す。

# 最初に確認するもの

- [`docs/review.md`](../../../docs/review.md)
- [`docs/architecture.md`](../../../docs/architecture.md)
- [`docs/implementation-guidelines.md`](../../../docs/implementation-guidelines.md)
- [`docs/unit-test.md`](../../../docs/unit-test.md)
- 差分と関連コード

# 手順

1. 比較基準を確定する（判断手順は `docs/review.md` の「比較範囲」を正本とする）。
2. 差分の目的、変更範囲、既存実装との関係を確認する。
3. `ui -> domain <- data` の依存方向が崩れていないか見る。
4. ViewModel、UseCase、Repository interface、Repository implementation、mapper、DAO の責務が混ざっていないか見る。
5. 新規 API、抽象化、DB schema、画面構成が本当に必要か確認する。
6. 変更した振る舞いに対するテストがあるか、テスト観点が妥当か確認する。
7. 指摘の分類、形式、終了時の報告は `docs/review.md` を正本とする。

# 注意事項

- 好みではなく、バグ、依存方向、責務分離、保守性、テスト不足を優先する。
- テストコードだけの実装・修正は `test-implementation` を優先する。
- 問題がない場合も、残るリスクや未検証項目を明記する。
- DB schema 変更がある場合は migration と schema JSON を必ず確認する。
- UI 変更がある場合は strings、preview、ライト / ダーク、イベント取りこぼしを確認する。
