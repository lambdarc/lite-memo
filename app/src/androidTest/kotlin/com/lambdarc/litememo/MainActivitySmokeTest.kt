package com.lambdarc.litememo

import androidx.annotation.StringRes
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.domain.usecase.CompleteTutorialUseCase
import com.lambdarc.litememo.ui.screen.MemoEditTestTags
import com.lambdarc.litememo.ui.screen.SettingsTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var completeTutorialUseCase: CompleteTutorialUseCase

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val createMemoButton by lazy { hasContentDescription(string(R.string.create_memo)) }
    private val backButton by lazy { hasContentDescription(string(R.string.navigate_back)) }
    private val titleInput = hasTestTag(MemoEditTestTags.TITLE_INPUT)
    private val bodyInput = hasTestTag(MemoEditTestTags.BODY_INPUT)

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { completeTutorialUseCase() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun normalMemoMovedToTrashCanBeRestoredToHome() {
        // Arrange
        createMemo(title = "Trip plan")
        awaitSingle(hasText("Trip plan"))

        // Act
        // Normal: a memo travels from Home to Trash and back through the real screens
        composeRule.onNodeWithText("Trip plan").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription(string(R.string.move_selected_memos_to_trash))
            .performClick()
        awaitGone(hasText("Trip plan"))
        val trashItem = openSettingsItem(R.string.settings_trash)
        awaitSingle(hasText("Trip plan"))
        composeRule.onNodeWithText("Trip plan").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription(string(R.string.more_options)).performClick()
        composeRule.onNodeWithText(string(R.string.restore_selected_memos)).performClick()
        awaitGone(hasText("Trip plan"))
        navigate(click = backButton, arriveAt = trashItem)
        openHomeTab()

        // Assert
        awaitSingle(hasText("Trip plan"))
    }

    @Test
    fun normalTagFilterShowsOnlyMemosWithThatTag() {
        // Arrange
        val tagManageItem = openSettingsItem(R.string.settings_tag_manage)
        composeRule.onNodeWithContentDescription(string(R.string.tag_create)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Work")
        composeRule.onNodeWithText(string(R.string.save_label)).performClick()
        awaitSingle(hasText("Work"))
        navigate(click = backButton, arriveAt = tagManageItem)
        openHomeTab()
        createMemo(title = "Tagged memo", tagName = "Work")
        createMemo(title = "Plain memo")
        awaitSingle(hasText("Plain memo"))

        // Act
        // Normal: a tag created in Tag management filters Home down to memos carrying it
        composeRule.onNode(hasText("Work") and isSelectable()).performClick()

        // Assert
        awaitGone(hasText("Plain memo"))
        awaitSingle(hasText("Tagged memo"))
    }

    @Test
    fun normalSystemBackWhileEditingKeepsTypedContent() {
        // Arrange
        navigate(click = createMemoButton, arriveAt = titleInput)
        composeRule.onNode(titleInput).performTextInput("Draft title")
        composeRule.onNode(bodyInput).performTextInput("Draft body")

        // Act
        // Normal: leaving the editor with system back saves what was typed before the debounce
        Espresso.pressBack()
        awaitSingle(hasText("Draft title"))
        navigate(click = hasText("Draft title"), arriveAt = bodyInput)

        // Assert
        composeRule.onNodeWithTag(MemoEditTestTags.BODY_INPUT).assertTextContains("Draft body")
    }

    private fun createMemo(title: String, tagName: String? = null) {
        navigate(click = createMemoButton, arriveAt = titleInput)
        composeRule.onNode(titleInput).performTextInput(title)
        if (tagName != null) {
            val tagChip = hasText(tagName) and isSelectable()
            awaitSingle(tagChip)
            composeRule.onNode(tagChip).performClick()
        }
        navigate(click = backButton, arriveAt = createMemoButton)
    }

    private fun openHomeTab() {
        navigate(click = tab(R.string.home_tab), arriveAt = createMemoButton)
    }

    private fun openSettingsItem(@StringRes labelResId: Int): SemanticsMatcher {
        navigate(
            click = tab(R.string.settings_tab),
            arriveAt = hasTestTag(SettingsTestTags.THEME_ROW)
        )
        val item = hasText(string(labelResId)) and !isSelectable()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(item)
        navigate(click = item, arriveAt = backButton)
        return item
    }

    private fun tab(@StringRes labelResId: Int): SemanticsMatcher =
        hasText(string(labelResId)) and isSelectable()

    private fun navigate(click: SemanticsMatcher, arriveAt: SemanticsMatcher) {
        // Navigation drops clicks until the current destination is resumed, so clicks are retried
        repeat(NAVIGATION_ATTEMPTS) {
            if (composeRule.onAllNodes(click).fetchSemanticsNodes().size == 1) {
                composeRule.onNode(click).performClick()
            }
            try {
                composeRule.waitUntilAtLeastOneExists(arriveAt, NAVIGATION_ATTEMPT_MILLIS)
                return
            } catch (_: ComposeTimeoutException) {
            }
        }
        throw AssertionError("Navigation did not reach the expected screen")
    }

    private fun awaitSingle(matcher: SemanticsMatcher) {
        composeRule.waitUntilExactlyOneExists(matcher, TIMEOUT_MILLIS)
    }

    private fun awaitGone(matcher: SemanticsMatcher) {
        composeRule.waitUntilDoesNotExist(matcher, TIMEOUT_MILLIS)
    }

    private fun string(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val NAVIGATION_ATTEMPTS = 3
        const val NAVIGATION_ATTEMPT_MILLIS = 2_000L
    }
}
