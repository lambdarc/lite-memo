package com.lambdarc.litememo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.component.ErrorContent
import com.lambdarc.litememo.ui.component.LoadingContent
import com.lambdarc.litememo.ui.component.MessageContent
import com.lambdarc.litememo.ui.component.tagColor
import com.lambdarc.litememo.ui.component.toComposeColor
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.TagEditUiState
import com.lambdarc.litememo.ui.state.TagManageUiState
import com.lambdarc.litememo.ui.theme.DEFAULT_TAG_COLORS
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

private const val HEX_RADIX = 16
private const val ARGB_HEX_DIGIT_COUNT = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    uiState: TagManageUiState,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (TagId) -> Unit,
    onDeleteRequest: (TagUiModel) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onEditColorSelect: (Long) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                title = { Text(text = stringResource(R.string.tag_manage_title)) }
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.hasError) {
                FloatingActionButton(onClick = onCreateClick) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.tag_create)
                    )
                }
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent(modifier = Modifier.padding(innerPadding))

            uiState.hasError -> ErrorContent(
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding)
            )

            uiState.tags.isEmpty() -> {
                MessageContent(
                    title = stringResource(R.string.tag_empty_title),
                    body = stringResource(R.string.tag_empty_body),
                    modifier = Modifier.padding(innerPadding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = uiState.tags,
                        key = { it.id.value }
                    ) { tag ->
                        TagRow(
                            tag = tag,
                            onEditClick = { onEditClick(tag.id) },
                            onDeleteClick = { onDeleteRequest(tag) }
                        )
                    }
                }
            }
        }
    }

    uiState.editingTag?.let { editState ->
        TagEditDialog(
            state = editState,
            onNameChange = onEditNameChange,
            onColorSelect = onEditColorSelect,
            onSave = onSaveEdit,
            onDismiss = onCancelEdit
        )
    }

    uiState.showDeleteDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(text = stringResource(R.string.tag_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.tag_delete_confirm_message, tag.name)
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = stringResource(R.string.delete_label),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(text = stringResource(R.string.cancel_label))
                }
            }
        )
    }
}

@Composable
private fun TagRow(tag: TagUiModel, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(tag.toComposeColor())
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = tag.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.tag_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditDialog(
    state: TagEditUiState,
    onNameChange: (String) -> Unit,
    onColorSelect: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = state.id == null

    AlertDialog(
        onDismissRequest = {
            if (!state.isSaving) onDismiss()
        },
        title = {
            Text(
                text = stringResource(
                    if (isNew) R.string.tag_create else R.string.tag_edit
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(text = stringResource(R.string.tag_name_hint)) },
                    isError = state.nameError || state.duplicateNameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.duplicateNameError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tag_duplicate_name_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.saveError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tag_save_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tag_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DEFAULT_TAG_COLORS.forEach { paletteColor ->
                        val colorArgb = paletteColor.argb
                        val isSelected = colorArgb == state.colorArgb
                        val colorCode = colorArgb.toString(HEX_RADIX)
                            .uppercase()
                            .padStart(ARGB_HEX_DIGIT_COUNT, '0')
                        val colorDescription = stringResource(
                            R.string.tag_color_option_content_description,
                            colorCode
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(tagColor(colorArgb))
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = colorDescription
                                }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onColorSelect(colorArgb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val selectedColor = tagColor(colorArgb)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = checkmarkTintFor(selectedColor)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(text = stringResource(R.string.save_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(text = stringResource(R.string.cancel_label))
            }
        }
    )
}

@Composable
private fun checkmarkTintFor(backgroundColor: Color): Color {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val darkerThemeColor = if (onSurface.luminance() <= surface.luminance()) onSurface else surface
    val lighterThemeColor = if (onSurface.luminance() > surface.luminance()) onSurface else surface

    return if (backgroundColor.luminance() > LIGHT_BACKGROUND_LUMINANCE_THRESHOLD) {
        darkerThemeColor.copy(alpha = CHECKMARK_TINT_ON_LIGHT_ALPHA)
    } else {
        lighterThemeColor
    }
}

private const val LIGHT_BACKGROUND_LUMINANCE_THRESHOLD = 0.5f
private const val CHECKMARK_TINT_ON_LIGHT_ALPHA = 0.87f

@Preview(showBackground = true)
@Composable
private fun TagManageScreenPreview() {
    LiteMemoTheme {
        TagManageScreen(
            uiState = TagManageUiState(
                isLoading = false,
                tags = listOf(
                    TagUiModel(TagId("1"), "仕事", 0xFFB3261E),
                    TagUiModel(TagId("2"), "生活", 0xFF6750A4),
                    TagUiModel(TagId("3"), "趣味", 0xFF006D3B)
                )
            ),
            onBackClick = {},
            onCreateClick = {},
            onEditClick = {},
            onDeleteRequest = {},
            onConfirmDelete = {},
            onDismissDelete = {},
            onEditNameChange = {},
            onEditColorSelect = {},
            onSaveEdit = {},
            onCancelEdit = {},
            onRetry = {}
        )
    }
}
