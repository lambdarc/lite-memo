package com.lambdarc.litememo.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.ui.component.ErrorContent
import com.lambdarc.litememo.ui.component.LoadingContent
import com.lambdarc.litememo.ui.component.MessageContent
import com.lambdarc.litememo.ui.model.TrashedMemoUiModel
import com.lambdarc.litememo.ui.state.TrashUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(uiState: TrashUiState, actions: TrashScreenActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TrashTopAppBar(uiState = uiState, actions = actions)
        }
    ) { innerPadding ->
        TrashScreenContent(
            uiState = uiState,
            actions = actions,
            modifier = Modifier.padding(innerPadding)
        )
    }

    if (uiState.showEmptyTrashDialog) {
        EmptyTrashConfirmDialog(actions = actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashTopAppBar(uiState: TrashUiState, actions: TrashScreenActions) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val canShowMenu = !uiState.hasError &&
        (uiState.selection.isActive || uiState.memos.isNotEmpty())

    LaunchedEffect(canShowMenu) {
        if (!canShowMenu) {
            isMenuExpanded = false
        }
    }

    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            TrashNavigationIcon(uiState = uiState, actions = actions)
        },
        title = {
            TrashTopBarTitle(uiState = uiState)
        },
        actions = {
            if (canShowMenu) {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
                TrashOverflowMenu(
                    expanded = isMenuExpanded,
                    selectionActive = uiState.selection.isActive,
                    onDismiss = { isMenuExpanded = false },
                    actions = actions
                )
            }
        }
    )
}

@Composable
private fun TrashNavigationIcon(uiState: TrashUiState, actions: TrashScreenActions) {
    IconButton(
        onClick = if (uiState.selection.isActive) {
            actions::onClearSelection
        } else {
            actions::onBackClick
        }
    ) {
        Icon(
            imageVector = if (uiState.selection.isActive) {
                Icons.Default.Close
            } else {
                Icons.AutoMirrored.Filled.ArrowBack
            },
            contentDescription = if (uiState.selection.isActive) {
                stringResource(R.string.clear_selection)
            } else {
                stringResource(R.string.navigate_back)
            }
        )
    }
}

@Composable
private fun TrashTopBarTitle(uiState: TrashUiState) {
    Text(
        text = if (uiState.selection.isActive) {
            pluralStringResource(
                R.plurals.selected_memo_count,
                uiState.selection.selectedCount,
                uiState.selection.selectedCount
            )
        } else {
            stringResource(R.string.trash_title)
        }
    )
}

@Composable
private fun TrashOverflowMenu(
    expanded: Boolean,
    selectionActive: Boolean,
    onDismiss: () -> Unit,
    actions: TrashScreenActions
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (selectionActive) {
            DropdownMenuItem(
                text = {
                    Text(text = stringResource(R.string.restore_selected_memos))
                },
                onClick = {
                    onDismiss()
                    actions.onRestoreSelectedMemos()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.trash_empty_action)) },
                onClick = {
                    onDismiss()
                    actions.onEmptyTrashRequest()
                }
            )
        }
    }
}

@Composable
private fun TrashScreenContent(
    uiState: TrashUiState,
    actions: TrashScreenActions,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingContent(modifier = modifier)

        uiState.hasError -> ErrorContent(
            onRetry = actions::onRetry,
            modifier = modifier
        )

        uiState.memos.isEmpty() -> MessageContent(
            title = stringResource(R.string.trash_empty_title),
            body = stringResource(R.string.trash_empty_body),
            modifier = modifier
        )

        else -> TrashedMemoList(
            uiState = uiState,
            actions = actions,
            modifier = modifier
        )
    }
}

@Composable
private fun TrashedMemoList(
    uiState: TrashUiState,
    actions: TrashScreenActions,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = uiState.memos,
            key = { it.id.value }
        ) { memo ->
            TrashedMemoCard(
                memo = memo,
                selected = uiState.selection.contains(memo.id),
                onClick = {
                    if (uiState.selection.isActive) {
                        actions.onMemoSelectionToggle(memo.id)
                    }
                },
                onLongClick = {
                    if (uiState.selection.isActive) {
                        actions.onMemoSelectionToggle(memo.id)
                    } else {
                        actions.onMemoLongClick(memo.id)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashedMemoCard(
    memo: TrashedMemoUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.select_trashed_memo),
            role = Role.Button
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                text = memo.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = memo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.trash_deleted_at_label,
                        deletedAtLabel(memo.deletedAt)
                    ),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyTrashConfirmDialog(actions: TrashScreenActions) {
    AlertDialog(
        onDismissRequest = actions::onDismissEmptyTrash,
        title = { Text(text = stringResource(R.string.trash_empty_confirm_title)) },
        text = { Text(text = stringResource(R.string.trash_empty_confirm_message)) },
        confirmButton = {
            TextButton(onClick = actions::onConfirmEmptyTrash) {
                Text(
                    text = stringResource(R.string.trash_empty_action),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = actions::onDismissEmptyTrash) {
                Text(text = stringResource(R.string.cancel_label))
            }
        }
    )
}

@Composable
private fun deletedAtLabel(deletedAt: TimestampMillis): String {
    val zoneId = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/M/d H:mm", Locale.getDefault()) }
    val deletedAtDateTime = remember(deletedAt, zoneId) {
        Instant.ofEpochMilli(deletedAt.value).atZone(zoneId)
    }
    return formatter.format(deletedAtDateTime)
}
