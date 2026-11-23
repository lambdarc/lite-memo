# Instrumented Test

この文書は Lite Memo の instrumented test（`src/androidTest`）の方針をまとめます。
JVM Unit Test（`src/test`）の方針は [`docs/unit-test.md`](unit-test.md) を正本とします。

## 役割と配置判断

instrumented test は JUnit 4 と AndroidX Test を使い、`AndroidJUnitRunner` を継承した `HiltTestRunner` で実行します。

実 Android 環境が必要な次の検証を `src/androidTest` に配置します。

- Android SQLite 上の Room DAO とマイグレーション
- 実際の `ContentResolver`、Provider、SAF との連携
- 実 Context、ファイルシステム、ライフサイクルとの統合が必要な振る舞い
- Android Framework の実挙動（`Intent.filterEquals` など）
- Compose UI の操作と表示分岐
- 画面をまたぐ Activity の E2E スモークテスト

ライブラリ名や層だけで一律に `src/androidTest` へ置かず、JVM で同じ振る舞いを検証できるものは [`docs/unit-test.md`](unit-test.md) 側を優先します。

## 共通ルール

- テスト関数名（英語・観点接頭辞）、AAA コメント、英語の検証観点コメントと前提コメント、fixture / ヘルパーへのコメント禁止方針は [`docs/unit-test.md`](unit-test.md) に揃える
- JUnit 4 の instrumented test / Compose UI Test では、同じシナリオを共同で検証する複数の assertion を直接記述してよい
- coroutine を扱うテストは `kotlinx.coroutines.test.runTest` を使う。E2E テストの `@Before` で行う実 I/O の初期化など `runTest` が適さない箇所に限り `runBlocking` を許容する

## クラス命名規約

テストクラス名は対象と役割に応じて次の接尾語を付けます。

- `ComposeTest`: Screen や Route 配下の Composable / effect を検証するテスト（例: `MemoEditScreenComposeTest`、`MemoEditUiResultEffectComposeTest`）
- `SmokeTest`: 画面をまたぐ Activity の E2E テスト（例: `MainActivitySmokeTest`）
- `InstrumentedTest`: 上記以外の instrumented test（例: `RoomDaoInstrumentedTest`、`LiteMemoMigrationInstrumentedTest`）

## `HiltTestRunner` と `@HiltAndroidTest`

- `testInstrumentationRunner` に指定している `HiltTestRunner` は、`@CustomTestApplication(Application::class)` が生成する素の `Application` へ差し替える
- これにより、`LiteMemoApplication.onCreate()` の MobileAds 初期化、import / export の起動時 cleanup、ウィジェット更新の Flow 収集が instrumented test 中に走らない
- 画面をまたぐ E2E テストのように Hilt グラフを伴うテストでは、`@HiltAndroidTest` と `HiltAndroidRule`（`order = 0`）を使い、`ActivityScenario` で Activity を起動する
- DB と DataStore は `@TestInstallIn` モジュール（`InMemoryDatabaseModule`、`TemporaryDataStoreModule`）でインメモリ DB と一時ファイルへ差し替えられる

## Room DAO と Migration のテスト

- Room DAO のテストは DAO ごとに分割せず、`RoomDaoInstrumentedTest` 1 クラスへ集約する
- Migration テストは `MigrationTestHelper` を使い、`app/schemas/` の schema JSON と `LiteMemoMigrations.ALL` を通した移行・制約を検証する

## Compose UI Test

- Compose ルールは `androidx.compose.ui.test.junit4.v2` の `createComposeRule`（Screen / Composable 単体）または `createEmptyComposeRule`（`ActivityScenario` と組み合わせる E2E）を使う
- 表示文言の直書きを避け、`R.string.*` と semantics matcher を基本とする。動的な文字列や重複などで semantics だけでは一意に特定しにくい要素へ test tag（`XxxTestTags`）を付ける
- Screen テストの共通ラッパーや UI モデル fixture は `ComposeScreenTestFixtures` を使う
- 必須 callback が多い Screen は、no-op を既定値にした private ヘルパーをテストクラス内に置き、各テストからは検証したい state や callback だけを渡す。Screen を直接呼ぶとテストごとに全 callback を書くことになり、引数変更のたびに全テストが壊れるため

Compose UI Test で同じ表示シナリオを検証する例:

```kotlin
@Test
fun normalImagesShowImageListAndItems() {
    // Arrange
    val image = testMemoImageUiModel()

    // Act
    // Normal: an image is shown in the image list
    setMemoEditScreen(uiState = { MemoEditUiState(images = listOf(image)) })

    // Assert
    composeRule.onNodeWithTag(MemoEditTestTags.IMAGE_LIST).assertIsDisplayed()
    composeRule.onNodeWithTag(MemoEditTestTags.imageItem(image.id)).assertIsDisplayed()
}
```

## テスト用 `ContentProvider` の実装制約

`app/src/androidTest/AndroidManifest.xml` に登録するテスト用 `ContentProvider`（`NonTruncatingExportTestProvider.java`）は、Kotlin ではなく `app/src/androidTest/java/` に Java で実装します。

この provider はテスト APK 側のプロセスで単独インスタンス化されますが、テスト APK の classpath にはアプリ APK と重複する `kotlin-stdlib` が含まれません。
Kotlin で実装すると `openFile()` 等で `NoClassDefFoundError`（`kotlin.jvm.internal.Intrinsics`）が発生してクラッシュするため、Java 実装を維持します。

## 実行

Instrumented Test / Compose UI Test の実行コマンドは [`docs/development-setup.md`](development-setup.md) を参照します。
