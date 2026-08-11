# Unit Test

この文書は Lite Memo の Unit Test / instrumented test 方針をまとめます。

## 基本方針

- JVM Unit Test（`src/test`）は JUnit Jupiter（現在は 6.x）を使う
- instrumented test（`src/androidTest`）は JUnit 4 と AndroidX Test を使い、`AndroidJUnitRunner` を継承した `HiltTestRunner` で実行する
- テスト関数名は英語にする
- 1つのテスト関数では、1つの振る舞いまたは1つのシナリオだけを検証する
- Domain の値オブジェクト、UseCase、Repository interface 境界を優先してテストする

## テスト配置の判断

ライブラリ名ではなく、検証する振る舞いが実 Android 環境を必要とするかで `src/test` と `src/androidTest` を選びます。

JVM Unit Test で優先してテストするもの:

- 値オブジェクトの生成条件と不正値
- UseCase の正常系と失敗系
- Repository interface を境界にした Domain の振る舞い
- mapper の変換ルール
- ViewModel の状態遷移と UI state の生成
- Android Framework の実動作を必要としない Repository 実装
- 一時ファイルや依存の差し替えで完結する DataStore の振る舞い

instrumented test で検証するもの:

- Android SQLite 上の Room DAO とマイグレーション
- 実際の `ContentResolver`、Provider、SAF との連携
- Android の Context、ファイルシステム、ライフサイクルとの統合が必要な DataStore の振る舞い
- 必要に応じた Compose UI の操作と表示分岐

DataStore や Repository という分類だけで一律に `androidTest` へ置かず、JVM で同じ振る舞いを検証できる場合は JVM Unit Test を優先します。

後回しにしてよいもの:

- Compose UI の細かい見た目
- まだ仕様が固まっていない画面構成

## 命名

- テストクラス名は対象クラス名に `Test` を付ける
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
- JVM Unit Test（JUnit Jupiter）で、同一の実行結果に含まれる複数のプロパティを個別に検証する場合は `assertAll` を使う
- instrumented test / Compose UI Test（JUnit 4）では、同じシナリオを共同で検証する複数の assertion を直接記述してよい
- `verify` / `coVerify` / `confirmVerified` など、同じ振る舞いを裏付ける interaction check は結果検証と併用してよい

JVM Unit Test で同一結果の複数プロパティを検証する例:

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

JUnit 4 の Compose UI Test で同じ表示シナリオを検証する例:

```kotlin
@Test
fun normalImagesShowImageListAndItems() {
    // Arrange
    val image = testMemoImageUiModel()

    // Act
    // Normal: an image is shown in the image list
    setMemoEditScreen(uiState = MemoEditUiState(images = listOf(image)))

    // Assert
    composeRule.onNodeWithTag(MemoEditTestTags.IMAGE_LIST).assertIsDisplayed()
    composeRule.onNodeWithTag(MemoEditTestTags.imageItem(image.id)).assertIsDisplayed()
}
```

Screen が多くの callback を必須引数に持つ場合は、no-op を既定値にした private ヘルパーを
テストクラス内に置き、各テストからは検証したい state だけを渡します。
Screen を直接呼ぶとテストごとに全 callback を書くことになり、変更のたびに全テストが壊れます。

## AAA Comments

- テストは Arrange / Act / Assert の流れで書く
- 基本は `// Arrange`、`// Act`、`// Assert` コメントを入れる
- Arrange が不要な場合は `// Arrange` を省略してよい
- Act と Assert は原則として分け、必要なラベルを別の表現へ置き換えない
- `assertThrows(Xxx::class.java) { ... }` や Turbine の `.test {}` のように、操作と検証が1つの式で構造上交錯する場合に限り、`// Act & Assert` を使う
- 検証観点は、`// Act` または `// Act & Assert` の直下に `// <観点>: <意図>` の形で1行添える

## テスト観点（命名とコメント）

テストが「何を検証しているか」を、関数名の接頭辞とコメントで明示します。

### 観点の種類

| 観点 | 意味 |
|------|------|
| `Normal` | 正常系。期待どおりの入力で期待どおりの結果になる |
| `Boundary` | 境界・特殊入力（空リスト、重複、空白のみ、no-op など） |
| `Error` | 失敗系。例外の送出やエラーイベントの発火 |
| `Interaction` | 依存（Repository / Provider など）の呼び出し有無・回数・順序の検証 |
| `Flow` | `StateFlow` / event stream の発火を検証 |
| `Coroutine` | 仮想時間・debounce・キャンセルなど coroutine の挙動 |
| `StateTransition` | 操作前後の UI state の遷移 |

### 適用ルール

- 関数名は観点を接頭辞に付ける（camelCase）。例: `normalUiStateLoadsExistingMemo`、`boundaryEmptyTagIdsSkipsTagValidation`、`flowSaveEmitsOperationErrorWhenMemoSaveFails`
- AAA ラベルは残したまま、`// Act` または `// Act & Assert` の直下に観点を1行置く。例: `// Boundary: blank input is rejected`
- 複数観点が絡む場合は `// Boundary/Interaction: ...` のように `/` で連結する
- 観点は検証の主目的に合わせて選び、無理に増やさない
- このルールは新規テストと、振る舞いを実質的に変更するテストへ適用する。既存テスト全体は、意図した移行作業でない限り一括変更しない

## Coroutine Test

- suspend 関数や Flow を扱う JVM Unit Test は `kotlinx.coroutines.test.runTest` を使う
- `runBlocking` は既存テストの保守を除き、新規・更新する JVM Unit Test では使わない
- 1つのテストで使う `TestDispatcher` は、同じ `TestCoroutineScheduler` を共有する
- `StandardTestDispatcher` は処理を scheduler にキューイングするため、実行順と仮想時間を明示的に制御したい場合の基本とする
- `UnconfinedTestDispatcher` は coroutine を即時に開始しやすいが、実行順の保証には使えない。即時開始が必要で順序が検証対象でない場合だけ意図して選ぶ
- ViewModel の `viewModelScope` を検証するときは、ViewModel 生成前に `Dispatchers.setMain(testDispatcher)` を行い、`@AfterEach` で `Dispatchers.resetMain()` する
- 終了しない Flow を手動で collect する job は `backgroundScope` で起動する。`StandardTestDispatcher` で起動した場合は、値を送る前に `runCurrent()` で購読開始を進める。即時開始だけが必要で順序が検証対象でない場合は、同じ scheduler の `UnconfinedTestDispatcher` を明示してよい。`backgroundScope` の job はテスト終了時にキャンセルされる
- `runTest` は仮想時間、TestScope 配下の child coroutine の完了待ち、未処理例外の伝播を支援する。一方で `Dispatchers.Main`、mock、singleton、ファイル、DB などのグローバル状態は復元せず、テスト間の分離も自動では行わない

同じ scheduler を `runTest` と `Dispatchers.Main` で共有する例:

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(scheduler)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateTransitionSaveStoresSuccessInUiState() = runTest(mainDispatcher) {
        // Arrange
        val viewModel = SaveViewModel()

        // Act & Assert
        // StateTransition: the save result is exposed in UI state
        viewModel.state.test {
            skipItems(1)
            viewModel.save()
            advanceUntilIdle()
            assertEquals(SaveUiState(result = SaveResult.Success), awaitItem())
        }
    }

    @Test
    fun stateTransitionAcknowledgeClearsSavedResult() = runTest(mainDispatcher) {
        // Arrange
        val viewModel = SaveViewModel()
        viewModel.save()
        advanceUntilIdle()

        // Act
        // StateTransition: the result is consumed once the UI acknowledges it
        viewModel.acknowledgeSaveResult()

        // Assert
        assertEquals(SaveUiState(), viewModel.state.value)
    }
}

private data class SaveUiState(
    val result: SaveResult? = null,
)

private sealed interface SaveResult {
    data object Success : SaveResult
}

private class SaveViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(SaveUiState())
    val state = mutableState.asStateFlow()

    fun save() {
        viewModelScope.launch {
            mutableState.value = SaveUiState(result = SaveResult.Success)
        }
    }

    fun acknowledgeSaveResult() {
        mutableState.value = SaveUiState()
    }
}
```

## Test Double と Flow の検証手段

- fake は、依存の状態や返却結果を単純に制御したい場合、同じテスト実装を複数箇所で再利用したい場合に使う
- MockK は、依存の呼び出し有無・回数・順序が主な検証対象の場合に使う。内部実装の細部まで固定する mock は避ける
- cold Flow の先頭値だけが必要なら `first()`、有限 Flow がちょうど1件であることまで検証するなら `single()` を使う。`StateFlow` の現在値は `value`、操作後の future emission は Turbine または意図を明示した `drop(1).first()` で検証する
- Turbine は、複数値の順序、未発火、時間制御、キャンセル、長寿命の `StateFlow` / event stream を検証するときに使う

## Instrumented Test

instrumented test は JUnit 4 と AndroidX Test の runner / rule を使います。
テスト関数名、AAA コメント、観点 prefix / comment の方針は JVM Unit Test とそろえ、coroutine を扱う場合だけ `runTest` を使います。

## 実行

Unit Test、coverage、Instrumented Test / Compose UI Test の実行コマンドは、[`development-setup.md`](development-setup.md) を参照します。
