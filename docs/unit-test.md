# Unit Test

この文書は Lite Memo の JVM Unit Test（`src/test`）の方針をまとめます。
実 Android 環境を使うテスト（`src/androidTest`）は [`docs/instrumented-test.md`](instrumented-test.md) を正本とします。

## 基本方針

- JVM Unit Test（`src/test`）は JUnit Jupiter を使う
- テスト関数名は英語にする
- 1つのテスト関数では、1つの振る舞いまたは1つのシナリオだけを検証する
- Domain の値オブジェクト、UseCase、Repository interface 境界を優先してテストする

## テスト配置の判断

ライブラリ名や層ではなく、検証する振る舞いが実 Android 環境を必要とするかで `src/test` と `src/androidTest` を選びます。
JVM で同じ振る舞いを検証できる場合は JVM Unit Test を優先します。

JVM Unit Test で優先してテストするもの:

- 値オブジェクトの生成条件と不正値
- UseCase の正常系と失敗系
- Repository interface を境界にした Domain の振る舞い
- mapper の変換ルール
- ViewModel の状態遷移と UI state の生成
- Android Framework の実動作を必要としない Repository 実装
- 一時ファイルや依存の差し替えで完結する DataStore の振る舞い

実 Android 環境が必要なテストの配置基準は [`docs/instrumented-test.md`](instrumented-test.md) を参照します。

後回しにしてよいもの:

- Compose UI の細かい見た目
- 仕様が固まっていない画面構成

## 命名

- JVM Unit Test のテストクラス名は対象クラス名に `Test` を付ける
- テスト規模が大きい対象は `<対象クラス名><関心事>Test`（例: `MemoEditViewModelImageTest`、`HomeViewModelSelectionTest`）へ分割してよい
- テスト関数名は英語で、検証する振る舞いが分かる名前にする
- 日本語の関数名やバッククォート関数名は使わない

例:

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoTitleTest {
    @Test
    fun normalConstructorReturnsTrimmedValueWhenInputHasSurroundingWhitespace() {
        // Arrange
        val input = "  shopping  "

        // Act
        // Normal: surrounding whitespace is removed
        val title = MemoTitle(input)

        // Assert
        assertEquals("shopping", title.value)
    }
}
```

## Assert

- 1つの振る舞いまたは1つのシナリオを成立させるために必要な、関連する複数の assertion は許容する
- 異なる入力条件、異なる操作、または同じシナリオを共同で説明しない異なる期待結果・異なる失敗理由を検証する場合は、テスト関数を分ける
- assertion 数を1つにするためだけに、`Pair`、`Triple`、専用 Snapshot 型、比較用 data class などへ値をまとめない
- 対象が自然に data class や値オブジェクトとして比較できる場合は、オブジェクト全体を `assertEquals` で比較する
- 期待値の全フィールドを書くと検証したい差分が埋もれる場合に限り、関係するプロパティだけを個別に検証する
- 同一の実行結果に含まれる複数のプロパティを個別に検証する場合は `assertAll` を使う
- `verify` / `coVerify` / `confirmVerified` など、同じ振る舞いを裏付ける interaction check は結果検証と併用してよい

同一結果の複数プロパティを検証する例:

```kotlin
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals

@Test
fun normalUiStateRestoresSavedEdits() {
    // Arrange
    val viewModel = createViewModelWithSavedEdits()

    // Act
    // Normal: saved edits are restored to the edit state
    val state = viewModel.uiState.value

    // Assert
    assertAll(
        { assertEquals("Shopping", state.title) },
        { assertEquals("Milk", state.body) }
    )
}
```

## AAA Comments

テストコードに書く基本のコメントは、AAA ラベルと検証観点の2種類です。
この節と次の「テスト観点（命名とコメント）」のコメント規約は、`src/androidTest` の instrumented test にも適用します。

- テストは Arrange / Act / Assert の流れで書く
- 基本は `// Arrange`、`// Act`、`// Assert` コメントを入れる
- Arrange が不要な場合は `// Arrange` を省略してよい
- Act と Assert は原則として分け、必要なラベルを別の表現へ置き換えない
- `assertThrows(Xxx::class.java) { ... }`、Turbine の `.test {}`、`kotest-property` の `checkAll { ... }` のように、操作と検証が1つの式で構造上交錯する場合に限り、`// Act & Assert` を使う
- 検証観点は、`// Act` または `// Act & Assert` の直下に `// <観点>: <意図>` の形で1行添え、**英語**で書く
- 上記に加えて、テスト対象の仕様や外部要因に由来する前提コメント（タイムゾーンの扱い、SQLite の collation、Room の行順が保証されないことなど、テストコードを読んでも導けない根拠）だけ書いてよい。前提コメントも**英語**で書く
- `// 観点: Normal - ...` のような別形式や、AAA ラベルへ説明を足した `// Assert: ...` は使わない
- 値の意図はテスト関数名と検証観点で表し、fixture の説明やヘルパー関数の KDoc は書かない

## テスト観点（命名とコメント）

テストが「何を検証しているか」を、関数名の接頭辞とコメントで明示します。

### 観点の種類

| 観点 | 意味 |
| --- | --- |
| `Normal` | 正常系。期待どおりの入力で期待どおりの結果になる |
| `Boundary` | 境界・特殊入力（空リスト、重複、空白のみ、no-op など） |
| `Error` | 失敗系。例外の送出やエラーイベントの発火 |
| `Interaction` | 依存（Repository / Provider など）の呼び出し有無・回数・順序の検証 |
| `Flow` | `StateFlow` / event stream の発火を検証 |
| `Coroutine` | 仮想時間・debounce・キャンセルなど coroutine の挙動 |
| `StateTransition` | 操作前後の UI state の遷移 |
| `Property` | 生成した任意の入力に対して成り立つ性質（往復変換、不変条件、例外の種類など） |

### 適用ルール

- 関数名は観点を接頭辞に付ける（camelCase）。例: `normalUiStateLoadsExistingMemo`、`boundaryEmptyTagIdsSkipsTagValidation`、`flowAutosaveFailureRetainsSaveFailed`
- AAA ラベルは残したまま、`// Act` または `// Act & Assert` の直下に英語で観点を1行置く。例: `// Boundary: blank input is rejected`
- 複数観点が絡む場合は `// Boundary/Interaction: ...` のように `/` で連結する
- 観点は検証の主目的に合わせて選び、無理に増やさない
- このルールは新規テストと、振る舞いを実質的に変更するテストへ適用する。既存テスト全体は、意図した移行作業でない限り一括変更しない

## テストケースの選び方

### 境界値分析

上限・下限・閾値を持つ条件は、境界の両側をテストします。

- on ポイント: 境界ちょうどの値（上限ちょうど、30 日ちょうど、デバウンス時間ちょうど）
- off ポイント: 境界を 1 単位越えた値（上限 + 1、999ms、`updatedAt` が `createdAt` の 1ms 前）
- 片側だけでは `>` と `>=` の取り違えを検出できないため、拒否側だけ・受理側だけで終わらせない
- 単位は条件の型に合わせる（バイト数なら 1 バイト、文字数なら 1 文字、時刻なら 1ms）
- 0 件・空・負値は、条件として明示されていなくても境界として扱う

### 同値分割

入力を「同じ扱いを受けるクラス」に分け、無効なクラスも含めて 1 クラスにつき代表値を 1 つテストします。

- 文字列の空白判定は、空文字、半角スペースのみ、全角スペース（U+3000）のみ、改行のみ、タブのみを別クラスとして扱う
- 「拒否される値に似ているが正しい値」（`.` / `..` に対する `...` や `.hidden`）を正常クラスとして入れる
- 同じ観点で複数のクラスを並べる場合は、次のパラメータ化テストを使う

### パラメータ化テスト

同じ振る舞いを複数の同値クラスで確かめる場合は、`@ParameterizedTest` と `@ValueSource` / `@MethodSource` を使います。

- 期待結果が異なるクラスを 1 つのパラメータ化テストへ混ぜない。受理するクラスと拒否するクラスは関数を分ける
- 関数名、AAA ラベル、観点コメントは通常のテストと同じ規則に従う
- 境界の on / off ポイントのように値ごとに意図が異なるものは、パラメータ化せず関数を分ける

```kotlin
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ParameterizedTest
@ValueSource(strings = ["", " ", "　", "\n", "\t"])
fun errorConstructorThrowsWhenValueIsBlank(input: String) {
    // Act & Assert
    // Error: every blank character class is rejected
    assertThrows(IllegalArgumentException::class.java) { TagName(input) }
}
```

### プロパティテスト

任意の入力で成り立つべき性質は、`kotest-property` の `checkAll` で検証します。
`kotest` のテストランナーは使わず、JUnit Jupiter の `@Test` から呼びます。

- 対象は往復変換（export → import、encode → decode）、不変条件（時刻が自分の日付範囲に入る）、失敗時の例外の種類など
- 例ベースのテストは境界ちょうどの値を固定し、プロパティテストは同値クラスの中を広く探る、という分担にする
- 文字列やタイムゾーンの生成には `app/src/test/kotlin/com/lambdarc/litememo/PropertyTestArbs.kt` の共通 `Arb`（`Arb.text()` 等）を使う
- `checkAll` は suspend 関数なので `runTest` の中で呼ぶ
- 1 回の検証が重い場合（アーカイブの読み書きなど）は `PropTestConfig(iterations = ...)` で回数を下げる
- seed は固定しない。失敗時は縮小された反例と `Repeat this test by using seed ...` が出力されるので、`PropTestConfig(seed = ...)` でローカル再現し、反例は例ベースのテストとして追加してから修正する

```kotlin
import com.lambdarc.litememo.text
import com.lambdarc.litememo.ui.navigation.encodeNavigationArgument
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Test
fun propertyEncodedArgumentDecodesToOriginal() = runTest {
    // Act & Assert
    // Property: any text survives an encode and decode round trip
    checkAll(PropTestConfig(iterations = 200), Arb.text()) { value ->
        val encoded = encodeNavigationArgument(value)
        assertEquals(value, URLDecoder.decode(encoded, StandardCharsets.UTF_8))
    }
}
```

## Coroutine Test

- suspend 関数や Flow を扱う JVM Unit Test は `kotlinx.coroutines.test.runTest` を使う
- JVM Unit Test では `runBlocking` を使わない
- 1つのテストで使う `TestDispatcher` は、同じ `TestCoroutineScheduler` を共有する
- `StandardTestDispatcher` は処理を scheduler にキューイングするため、実行順と仮想時間を明示的に制御したい場合の基本とする
- `UnconfinedTestDispatcher` は coroutine を即時に開始しやすいが、実行順の保証には使えない。即時開始が必要で順序が検証対象でない場合だけ意図して選ぶ
- ViewModel の `viewModelScope` を検証するときは、`@BeforeEach` で `StandardTestDispatcher()` を生成して `Dispatchers.setMain(dispatcher)` を設定し、`@AfterEach` で `Dispatchers.resetMain()` する。各テストは `runTest(dispatcher)` で同じ dispatcher を共有する（実例は `app/src/test/kotlin/com/lambdarc/litememo/ui/viewmodel/` 配下のテストを参照）
- 終了しない Flow を手動で collect する job は `backgroundScope` で起動する。`StandardTestDispatcher` で起動した場合は、値を送る前に `runCurrent()` で購読開始を進める。即時開始だけが必要で順序が検証対象でない場合は、同じ scheduler の `UnconfinedTestDispatcher` を明示してよい。`backgroundScope` の job はテスト終了時にキャンセルされる
- `runTest` は仮想時間、TestScope 配下の child coroutine の完了待ち、未処理例外の伝播を支援する。一方で `Dispatchers.Main`、mock、singleton、ファイル、DB などのグローバル状態は復元せず、テスト間の分離も自動では行わない

## Test Double と Flow の検証手段

- fake は、依存の状態や返却結果を単純に制御したい場合、同じテスト実装を複数箇所で再利用したい場合に使う
- 共有 Fake と fixture（`DomainTestFixtures` や各パッケージの `*TestFixtures`）が既にある場合は重複作成せず再利用する
- MockK は、依存の呼び出し有無・回数・順序が主な検証対象の場合に使う。内部実装の細部まで固定する mock は避ける
- cold Flow の先頭値だけが必要なら `first()`、特定条件を満たす状態まで待つなら条件付き `first { ... }`（または `filterIsInstance<...>().first { ... }`）、有限 Flow がちょうど1件であることまで検証するなら `single()` を使う。`StateFlow` の現在値は `value`、操作後の future emission は Turbine または意図を明示した `drop(1).first()` で検証する
- Turbine は、複数値の順序、未発火、時間制御、キャンセル、長寿命の `StateFlow` / event stream を検証するときに使う

## 関連文書と実行

- Instrumented Test / Compose UI Test の方針は [`docs/instrumented-test.md`](instrumented-test.md) を参照する
- Unit Test、coverage、Instrumented Test の実行コマンドは [`docs/development-setup.md`](development-setup.md) を参照する
