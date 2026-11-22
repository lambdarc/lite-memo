package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.ui.state.SettingsImportErrorDialogUiState
import com.lambdarc.litememo.ui.state.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalSettingsRowsAreDisplayed() {
        // Arrange
        val uiState = SettingsUiState(appVersion = "1.0.0")

        // Act
        setSettingsScreen(uiState = { uiState })

        // Assert
        listOf(
            R.string.settings_theme,
            R.string.settings_sort_order,
            R.string.settings_app_lock,
            R.string.settings_export,
            R.string.settings_import
        ).forEach { stringId ->
            composeRule
                .onNodeWithText(string(stringId))
                .assertIsDisplayed()
        }
    }

    @Test
    fun interactionImportDialogOpens() {
        // Arrange
        var uiState by mutableStateOf(SettingsUiState(appVersion = "1.0.0"))
        setSettingsScreen(
            uiState = { uiState },
            onImportClick = {
                uiState = uiState.copy(showImportConfirmDialog = true)
            }
        )

        // Act
        // Interaction: import action opens the confirm dialog.
        composeRule
            .onNodeWithTag(SettingsTestTags.IMPORT_ACTION)
            .performClick()

        // Assert
        composeRule
            .onNodeWithText(string(R.string.settings_import_confirm_title))
            .assertIsDisplayed()
    }

    @Test
    fun stateTransitionImportLoadingShowsProgress() {
        // Arrange
        val uiState = SettingsUiState(
            appVersion = "1.0.0",
            isImporting = true
        )

        // Act
        setSettingsScreen(uiState = { uiState })

        // Assert
        composeRule
            .onNodeWithTag(SettingsTestTags.IMPORT_LOADING_INDICATOR)
            .assertIsDisplayed()
    }

    @Test
    fun normalImportErrorDialogListsEveryConflictingTagName() {
        // Arrange
        val uiState = SettingsUiState(
            appVersion = "1.0.0",
            importErrorDialog = SettingsImportErrorDialogUiState.TagNameConflict(
                tagNames = listOf("Home", "Work")
            )
        )

        // Act
        setSettingsScreen(uiState = { uiState })

        // Assert
        listOf(
            string(R.string.settings_import_error_title),
            string(R.string.settings_import_error_tag_conflict_message),
            "Home",
            "Work"
        ).forEach { text ->
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun boundaryImportErrorDialogScrollsToTheLastTagName() {
        // Arrange
        val tagNames = (1..20).map { "タグ$it" }
        val uiState = SettingsUiState(
            appVersion = "1.0.0",
            importErrorDialog = SettingsImportErrorDialogUiState.TagNameConflict(tagNames)
        )
        setSettingsScreen(uiState = { uiState })

        // Act
        // Boundary: a long name list stays reachable through the scrollable text area.
        composeRule
            .onNodeWithTag(SettingsTestTags.IMPORT_ERROR_DIALOG_TEXT)
            .performScrollToNode(hasText("タグ20"))

        // Assert
        composeRule.onNodeWithText("タグ20").assertIsDisplayed()
    }

    @Test
    fun interactionImportErrorDialogCloseActionRequestsDismiss() {
        // Arrange
        var uiState by mutableStateOf(
            SettingsUiState(
                appVersion = "1.0.0",
                importErrorDialog = SettingsImportErrorDialogUiState.Generic
            )
        )
        setSettingsScreen(
            uiState = { uiState },
            onDismissImportErrorDialog = {
                uiState = uiState.copy(importErrorDialog = null)
            }
        )

        // Act
        // Interaction: closing the dialog releases the retained failure state.
        composeRule.onNodeWithTag(SettingsTestTags.IMPORT_ERROR_DIALOG_CLOSE).performClick()

        // Assert
        composeRule
            .onNodeWithText(string(R.string.settings_import_error_title))
            .assertDoesNotExist()
    }

    @Test
    fun normalDropdownsAreInitiallyClosed() {
        // Arrange
        val uiState = SettingsUiState()

        // Act
        setSettingsScreen(uiState = { uiState })

        // Assert
        composeRule.onNodeWithTag(SettingsTestTags.THEME_MENU).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_MENU).assertDoesNotExist()
    }

    @Test
    fun stateTransitionThemeRowOpensMenu() {
        // Arrange
        setSettingsScreen(uiState = { SettingsUiState() })

        // Act
        // StateTransition: tapping the theme row opens its menu.
        composeRule.onNodeWithTag(SettingsTestTags.THEME_ROW).performClick()

        // Assert
        composeRule.onNodeWithTag(SettingsTestTags.THEME_MENU).assertIsDisplayed()
    }

    @Test
    fun stateTransitionSortOrderRowOpensMenu() {
        // Arrange
        setSettingsScreen(uiState = { SettingsUiState() })

        // Act
        // StateTransition: tapping the sort order row opens its menu.
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_ROW).performClick()

        // Assert
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_MENU).assertIsDisplayed()
    }

    @Test
    fun interactionThemeOptionSelectsModeAndClosesMenu() {
        // Arrange
        var uiState by mutableStateOf(SettingsUiState(themeMode = ThemeMode.SYSTEM))
        val selectedModes = mutableListOf<ThemeMode>()
        setSettingsScreen(
            uiState = { uiState },
            onThemeModeSelect = {
                selectedModes.add(it)
                uiState = uiState.copy(themeMode = it)
            }
        )
        composeRule.onNodeWithTag(SettingsTestTags.THEME_ROW).performClick()

        // Act
        // Interaction: choosing a theme reports it once and shows it on the row.
        composeRule.onNodeWithTag(SettingsTestTags.themeOption(ThemeMode.DARK)).performClick()

        // Assert
        composeRule.onNodeWithTag(SettingsTestTags.THEME_MENU).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.THEME_ROW)
            .assertTextContains(string(R.string.settings_theme_dark))
        composeRule.runOnIdle { assertEquals(listOf(ThemeMode.DARK), selectedModes) }
    }

    @Test
    fun interactionSortOrderOptionSelectsOrderAndClosesMenu() {
        // Arrange
        var uiState by mutableStateOf(
            SettingsUiState(memoSortOrder = MemoSortOrder.UPDATED_NEWEST)
        )
        val selectedOrders = mutableListOf<MemoSortOrder>()
        setSettingsScreen(
            uiState = { uiState },
            onMemoSortOrderSelect = {
                selectedOrders.add(it)
                uiState = uiState.copy(memoSortOrder = it)
            }
        )
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_ROW).performClick()

        // Act
        // Interaction: choosing a sort order reports it once and shows it on the row.
        composeRule
            .onNodeWithTag(SettingsTestTags.sortOrderOption(MemoSortOrder.CREATED_NEWEST))
            .performClick()

        // Assert
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_MENU).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SORT_ORDER_ROW)
            .assertTextContains(string(R.string.settings_sort_created))
        composeRule.runOnIdle {
            assertEquals(listOf(MemoSortOrder.CREATED_NEWEST), selectedOrders)
        }
    }

    private fun setSettingsScreen(
        uiState: () -> SettingsUiState,
        onThemeModeSelect: (ThemeMode) -> Unit = {},
        onMemoSortOrderSelect: (MemoSortOrder) -> Unit = {},
        onImportClick: () -> Unit = {},
        onDismissImportErrorDialog: () -> Unit = {}
    ) {
        composeRule.setContent {
            TestScreenContent {
                SettingsScreen(
                    uiState = uiState(),
                    onThemeModeSelect = onThemeModeSelect,
                    onMemoSortOrderSelect = onMemoSortOrderSelect,
                    onAppLockEnabledChange = {},
                    onTagManageClick = {},
                    onTrashClick = {},
                    onExportClick = {},
                    onImportClick = onImportClick,
                    onConfirmImport = {},
                    onDismissImportConfirmDialog = {},
                    onDismissImportErrorDialog = onDismissImportErrorDialog,
                    onPrivacyPolicyClick = {},
                    onOpenSourceLicenseClick = {}
                )
            }
        }
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
