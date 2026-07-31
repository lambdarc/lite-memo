---
name: test-implementation
description: Lite Memo のテストコードの追加、修正、テスト固有レビューを扱う。JUnit Jupiter（JVM Unit Test）、JUnit 4（instrumented test）、runTest、MockK、Turbine、ViewModel test、UseCase test、mapper test、Room/Compose instrumented test、AAA、観点 prefix が対象。
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
4. 変更内容に該当する検証をすべて実行する。Kotlin のテスト変更は `./gradlew :app:ktlintCheck :app:detekt`、JVM Unit Test は `./gradlew :app:testProdDebugUnitTest`、lint は `./gradlew :app:lintProdDebug`、instrumented test は `./gradlew :app:connectedDevDebugAndroidTest` で検証し、複数に該当するときは各 task を組み合わせる。
5. 追加・修正したテスト、実行した task と結果、未実施の検証と理由を簡潔に報告する。

# 注意事項

- 新規・更新する Unit Test では `runBlocking` を使わず `runTest` を使い、suspend / Flow / Channel / debounce / coroutine は必要な test dispatcher を使う。
- アプリ実装差分全体のレビューは `implementation-review` を優先する。
- Flow や one-shot event の検証には必要に応じて Turbine を使う。
- MockK の verify は呼び出し有無・回数・順序が主目的のときに使う。
- テスト名は英語、観点 prefix、AAA コメント、原則 1 主要 assert を守る。
- 複数の振る舞いを 1 テストに詰め込まない。
- Compose の細かい見た目より、状態遷移、表示分岐、ユーザー操作の結果を優先する。
- Room migration、DataStore、ContentResolver、Compose UI は必要に応じて instrumented test に置く。
