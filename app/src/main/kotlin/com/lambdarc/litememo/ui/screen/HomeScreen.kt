package com.lambdarc.litememo.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.component.ErrorContent
import com.lambdarc.litememo.ui.component.LoadingContent
import com.lambdarc.litememo.ui.component.MemoCard
import com.lambdarc.litememo.ui.component.MessageContent
import com.lambdarc.litememo.ui.component.SearchTopBar
import com.lambdarc.litememo.ui.component.tagColor
import com.lambdarc.litememo.ui.component.toComposeColor
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.HomeFilterUiState
import com.lambdarc.litememo.ui.state.HomeUiState
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onFilterSelect: (HomeFilterUiState) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMemoLongClick: (MemoId) -> Unit,
    onMemoSelectionToggle: (MemoId) -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelectedMemosToTrash: () -> Unit,
    onSetSelectedMemosFavorite: (Boolean) -> Unit,
    onRequestToggleTagForSelectedMemos: () -> Unit,
    onToggleSelectedMemosTag: (TagId) -> Unit,
    onDismissBulkTagDialog: () -> Unit,
    onShareSelectedMemo: () -> Unit,
    onMemoClick: (MemoId) -> Unit,
    onCreateMemoClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!uiState.selection.isActive) {
                FloatingActionButton(onClick = onCreateMemoClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_memo)
                    )
                }
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent()

            uiState.hasError -> ErrorContent(onRetry = onRetry)

            else -> HomeContent(
                uiState = uiState,
                onFilterSelect = onFilterSelect,
                onSearchToggle = onSearchToggle,
                onSearchQueryChange = onSearchQueryChange,
                onMemoLongClick = onMemoLongClick,
                onMemoSelectionToggle = onMemoSelectionToggle,
                onClearSelection = onClearSelection,
                onMoveSelectedMemosToTrash = onMoveSelectedMemosToTrash,
                onSetSelectedMemosFavorite = onSetSelectedMemosFavorite,
                onRequestToggleTagForSelectedMemos = onRequestToggleTagForSelectedMemos,
                onShareSelectedMemo = onShareSelectedMemo,
                onMemoClick = onMemoClick,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    HomeBulkTagDialog(
        uiState = uiState,
        onToggleSelectedMemosTag = onToggleSelectedMemosTag,
        onDismiss = onDismissBulkTagDialog
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onFilterSelect: (HomeFilterUiState) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMemoLongClick: (MemoId) -> Unit,
    onMemoSelectionToggle: (MemoId) -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelectedMemosToTrash: () -> Unit,
    onSetSelectedMemosFavorite: (Boolean) -> Unit,
    onRequestToggleTagForSelectedMemos: () -> Unit,
    onShareSelectedMemo: () -> Unit,
    onMemoClick: (MemoId) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.selection.isActive) {
            stickyHeader {
                HomeSelectionToolbar(
                    selectedCount = uiState.selection.selectedCount,
                    allSelectedFavorite = uiState.allSelectedFavorite,
                    onClearSelection = onClearSelection,
                    onMoveToTrash = onMoveSelectedMemosToTrash,
                    onToggleFavorite = {
                        onSetSelectedMemosFavorite(!uiState.allSelectedFavorite)
                    },
                    onToggleTag = onRequestToggleTagForSelectedMemos,
                    onShareSelectedMemo = onShareSelectedMemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        } else {
            item {
                SearchTopBar(
                    search = uiState.search,
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange
                )
            }
        }
        if (uiState.search.isActive) {
            when {
                uiState.search.hasError -> item {
                    MessageContent(
                        title = stringResource(R.string.search_error_title),
                        body = stringResource(R.string.search_error_body)
                    )
                }

                uiState.search.query.isBlank() -> item {
                    MessageContent(
                        title = stringResource(R.string.search_hint),
                        body = stringResource(R.string.search_hint_body)
                    )
                }

                uiState.search.results.isEmpty() -> item {
                    MessageContent(
                        title = stringResource(R.string.no_search_results_title),
                        body = stringResource(R.string.no_search_results_body)
                    )
                }

                else -> items(
                    items = uiState.search.results,
                    key = { memo -> memo.id.value }
                ) { memo ->
                    SelectableMemoCard(
                        memo = memo,
                        isSelectionActive = uiState.selection.isActive,
                        selected = uiState.selection.contains(memo.id),
                        onMemoClick = onMemoClick,
                        onMemoSelectionToggle = onMemoSelectionToggle,
                        onMemoLongClick = onMemoLongClick
                    )
                }
            }
        } else {
            item {
                HomeFilterRow(
                    selectedFilter = uiState.selectedFilter,
                    tags = uiState.tags,
                    onFilterSelect = onFilterSelect
                )
            }
            if (uiState.memos.isEmpty()) {
                item {
                    EmptyHomeContent()
                }
            } else {
                items(
                    items = uiState.memos,
                    key = { memo -> memo.id.value }
                ) { memo ->
                    SelectableMemoCard(
                        memo = memo,
                        isSelectionActive = uiState.selection.isActive,
                        selected = uiState.selection.contains(memo.id),
                        onMemoClick = onMemoClick,
                        onMemoSelectionToggle = onMemoSelectionToggle,
                        onMemoLongClick = onMemoLongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableMemoCard(
    memo: MemoUiModel,
    isSelectionActive: Boolean,
    selected: Boolean,
    onMemoClick: (MemoId) -> Unit,
    onMemoSelectionToggle: (MemoId) -> Unit,
    onMemoLongClick: (MemoId) -> Unit
) {
    MemoCard(
        memo = memo,
        onClick = {
            if (isSelectionActive) {
                onMemoSelectionToggle(memo.id)
            } else {
                onMemoClick(memo.id)
            }
        },
        onLongClick = { onMemoLongClick(memo.id) },
        selected = selected
    )
}

@Composable
private fun HomeSelectionToolbar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    onClearSelection: () -> Unit,
    onMoveToTrash: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTag: () -> Unit,
    onShareSelectedMemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.clear_selection)
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.selected_memo_count,
                selectedCount,
                selectedCount
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onMoveToTrash) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.move_selected_memos_to_trash),
                tint = MaterialTheme.colorScheme.error
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (allSelectedFavorite) {
                    Icons.Default.Star
                } else {
                    Icons.Default.StarBorder
                },
                contentDescription = if (allSelectedFavorite) {
                    stringResource(R.string.remove_selected_memos_from_favorite)
                } else {
                    stringResource(R.string.add_selected_memos_to_favorite)
                }
            )
        }
        IconButton(onClick = onToggleTag) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = stringResource(R.string.toggle_tag_for_selected_memos)
            )
        }
        SelectionMoreMenu(
            enabled = selectedCount == 1,
            onShareClick = onShareSelectedMemo
        )
    }
}

@Composable
private fun HomeBulkTagDialog(
    uiState: HomeUiState,
    onToggleSelectedMemosTag: (TagId) -> Unit,
    onDismiss: () -> Unit
) {
    if (!uiState.bulkTagDialog.isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.toggle_tag_for_selected_memos)) },
        text = {
            if (uiState.tags.isEmpty()) {
                Text(text = stringResource(R.string.home_bulk_tag_empty_body))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(
                        items = uiState.tags,
                        key = { tag -> tag.id.value }
                    ) { tag ->
                        val tagId = tag.id
                        FilterChip(
                            selected = tagId in uiState.allSelectedTagIds,
                            onClick = { onToggleSelectedMemosTag(tagId) },
                            label = {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tag.toComposeColor())
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel_label))
            }
        }
    )
}

@Composable
private fun HomeFilterRow(
    selectedFilter: HomeFilterUiState,
    tags: List<TagUiModel>,
    onFilterSelect: (HomeFilterUiState) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterButton(
            label = stringResource(R.string.filter_all),
            selected = selectedFilter == HomeFilterUiState.All,
            onClick = { onFilterSelect(HomeFilterUiState.All) }
        )
        FilterButton(
            label = stringResource(R.string.unorganized_label),
            selected = selectedFilter == HomeFilterUiState.Unorganized,
            onClick = { onFilterSelect(HomeFilterUiState.Unorganized) }
        )
        FilterButton(
            label = stringResource(R.string.filter_favorite),
            selected = selectedFilter == HomeFilterUiState.Favorite,
            onClick = { onFilterSelect(HomeFilterUiState.Favorite) }
        )
        tags.forEach { tag ->
            val tagFilter = HomeFilterUiState.ByTag(tag.id)
            FilterButton(
                label = tag.name,
                selected = selectedFilter == tagFilter,
                onClick = { onFilterSelect(tagFilter) },
                colorArgb = tag.colorArgb
            )
        }
    }
}

@Composable
private fun SelectionMoreMenu(enabled: Boolean, onShareClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.share_memo)) },
                onClick = {
                    expanded = false
                    onShareClick()
                }
            )
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colorArgb: Long? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = colorArgb?.let {
            {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tagColor(it))
                )
            }
        }
    )
}

@Composable
private fun EmptyHomeContent() {
    MessageContent(
        title = stringResource(R.string.empty_home_title),
        body = stringResource(R.string.empty_home_body)
    )
}

@Preview(showBackground = true)
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HomeScreenPreview() {
    val uiState = HomeUiState(
        isLoading = false,
        tags = listOf(
            TagUiModel(TagId("tag-life"), "生活", 0xFF6750A4),
            TagUiModel(TagId("tag-work"), "仕事", 0xFFB3261E)
        ),
        memos = listOf(
            MemoUiModel(
                id = MemoId("memo-1"),
                title = "買い物リスト",
                body = "卵、牛乳、コーヒー豆。帰りに駅前で買う。",
                tags = listOf(TagUiModel(TagId("tag-life"), "生活", 0xFF6750A4)),
                updatedAtMillis = System.currentTimeMillis(),
                isFavorite = false
            ),
            MemoUiModel(
                id = MemoId("memo-2"),
                title = "会議メモ",
                body = "次回までに画面構成と保存方式を確認する。",
                tags = listOf(TagUiModel(TagId("tag-work"), "仕事", 0xFFB3261E)),
                updatedAtMillis = System.currentTimeMillis(),
                isFavorite = true
            )
        )
    )

    LiteMemoTheme {
        HomeScreen(
            uiState = uiState,
            onFilterSelect = {},
            onSearchToggle = {},
            onSearchQueryChange = {},
            onMemoLongClick = {},
            onMemoSelectionToggle = {},
            onClearSelection = {},
            onMoveSelectedMemosToTrash = {},
            onSetSelectedMemosFavorite = {},
            onRequestToggleTagForSelectedMemos = {},
            onToggleSelectedMemosTag = {},
            onDismissBulkTagDialog = {},
            onShareSelectedMemo = {},
            onMemoClick = {},
            onCreateMemoClick = {},
            onRetry = {}
        )
    }
}
