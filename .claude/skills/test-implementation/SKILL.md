---
name: test-implementation
description: テストコードを追加・修正する依頼で使う。Lite Memo の JVM Unit Test（JUnit Jupiter）と instrumented test（JUnit 4）を担当する。
---

# 目的

Lite Memo の変更した振る舞いを、既存の Unit Test / instrumented test 方針に沿って検証する。

# 最初に確認するもの

- [`docs/unit-test.md`](../../../docs/unit-test.md)
- 対象コードと対応する [`app/src/test/`](../../../app/src/test/) または [`app/src/androidTest/`](../../../app/src/androidTest/)
- 必要に応じて [`app/build.gradle.kts`](../../../app/build.gradle.kts)

# 手順

1. 変更した振る舞いを洗い出し、JVM Unit Test で足りるか androidTest が要るか判断する。
2. domain の value object / UseCase / Repository interface 境界 / mapper を優先し、テスト対象を選ぶ。
3. 選んだ対象に AAA でテストを追加・修正する。
4. 変更した振る舞いと影響範囲に応じて検証を選ぶ。選択方針と実行コマンドは [`docs/development-setup.md`](../../../docs/development-setup.md) の「ローカルでの検証」に従う。
5. 追加・修正したテスト、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- テストの書き方（`runTest` と test dispatcher、AAA、観点 prefix、命名、1振る舞い / 1シナリオ、JUnit Jupiter の `assertAll`、Turbine / MockK の使い分け、instrumented test への振り分け）は `docs/unit-test.md` を正本とする。規約本文はここで複製しない。
- アプリ実装差分全体のレビューは `implementation-review` を優先する。
- 実装変更に付随するテストは、対応する層の Skill と併用する。
