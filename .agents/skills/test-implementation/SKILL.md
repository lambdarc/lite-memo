---
name: test-implementation
description: テストを書く、直す、増やす依頼で使う。Lite Memo のテストコード（JUnit Jupiter の JVM Unit Test、JUnit 4 の instrumented test、runTest、MockK、Turbine、ViewModel test、UseCase test、mapper test、Room / Compose instrumented test、AAA、観点 prefix）を扱う。「テスト書いて」「◯◯のテストを追加して」「テストが足りない」「runBlocking を直したい」「Flow の検証はこのプロジェクトだとどう書く？」のように、対象クラス名だけを指定された場合も、そのクラスがどの層にあるかに関係なくこの skill を使う。実装変更に付随するテストは各層の skill、差分全体のレビューは implementation-review を優先する。
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
4. 変更内容に該当する検証をすべて実行する。Kotlin のテスト変更は `./gradlew :app:ktlintCheck :app:detekt`、JVM Unit Test は `./gradlew :app:testProdDebugUnitTest`、lint は `./gradlew :app:lintProdDebug`、instrumented test は `./gradlew :app:connectedDevDebugAndroidTest` で検証し、複数に該当するときは各 task を組み合わせる。task 一覧の正本は [`docs/development-setup.md`](../../../docs/development-setup.md)。
5. 追加・修正したテスト、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- テストの書き方（`runTest` と test dispatcher、AAA、観点 prefix、命名、原則 1 主要 assert、Turbine / MockK の使い分け、instrumented test への振り分け）は `docs/unit-test.md` を正本とする。規約本文はここで複製しない。
- アプリ実装差分全体のレビューは `implementation-review` を優先する。
- 実装変更に付随するテストは、対応する層の Skill と併用する。
