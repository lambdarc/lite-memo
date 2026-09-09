package com.lambdarc.litememo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.component.ErrorContent
import com.lambdarc.litememo.ui.component.LoadingContent
import com.lambdarc.litememo.ui.component.MemoImageThumbnail
import com.lambdarc.litememo.ui.component.toComposeColor
import com.lambdarc.litememo.ui.state.MemoEditUiState
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoEditScreen(
    uiState: MemoEditUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTagToggle: (TagId) -> Unit,
    onDelete: () -> Unit,
    onBackRequest: () -> Unit,
    onRetry: () -> Unit,
    onRetryTags: () -> Unit,
    onAttachImageRequest: () -> Unit,
    onImageRemove: (String) -> Unit,
    onShareMemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canEdit = !uiState.isLoading && !uiState.hasError && !uiState.isDeletePending
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MemoEditTopBar(
                uiState = uiState,
                canEdit = canEdit,
                onBackRequest = onBackRequest,
                onAttachImageRequest = onAttachImageRequest,
                onShareMemo = onShareMemo,
                onDelete = onDelete
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent()

            uiState.hasError -> ErrorContent(onRetry = onRetry)

            else -> {
                val colorScheme = MaterialTheme.colorScheme
                val bodyFocusRequester = remember { FocusRequester() }
                LaunchedEffect(uiState.memoId) {
                    if (uiState.memoId == null) {
                        withFrameNanos { }
                        runCatching { bodyFocusRequester.requestFocus() }
                    }
                }
                val titleLabel = stringResource(R.string.memo_edit_title_hint)
                val bodyLabel = stringResource(R.string.memo_edit_body_hint)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = onTitleChange,
                        enabled = canEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = titleLabel }
                            .testTag(MemoEditTestTags.TITLE_INPUT),
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.memo_edit_title_hint),
                                    style = TextStyle(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (uiState.hasTagError) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.memo_edit_tag_load_error),
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onRetryTags) {
                                Text(text = stringResource(R.string.retry_label))
                            }
                        }
                    } else if (uiState.availableTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.availableTags.forEach { tag ->
                                val selected = tag.id in uiState.selectedTagIds
                                FilterChip(
                                    selected = selected,
                                    onClick = { onTagToggle(tag.id) },
                                    enabled = canEdit,
                                    label = { Text(text = tag.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(tag.toComposeColor())
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.images.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(MemoEditTestTags.IMAGE_LIST),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.images, key = { it.id }) { image ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .testTag(MemoEditTestTags.imageItem(image.id))
                                ) {
                                    MemoImageThumbnail(
                                        imagePath = image.filePath,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { onImageRemove(image.id) },
                                        enabled = canEdit,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(32.dp)
                                            .testTag(MemoEditTestTags.removeImageButton(image.id))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(
                                                R.string.memo_edit_remove_image
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    BasicTextField(
                        value = uiState.body,
                        onValueChange = onBodyChange,
                        enabled = canEdit,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(bodyFocusRequester)
                            .semantics { contentDescription = bodyLabel }
                            .testTag(MemoEditTestTags.BODY_INPUT),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.body.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.memo_edit_body_hint),
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoEditTopBar(
    uiState: MemoEditUiState,
    canEdit: Boolean,
    onBackRequest: () -> Unit,
    onAttachImageRequest: () -> Unit,
    onShareMemo: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = onBackRequest) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back)
                )
            }
        },
        title = {},
        actions = {
            if (canEdit) {
                IconButton(
                    onClick = onAttachImageRequest,
                    modifier = Modifier.testTag(MemoEditTestTags.ATTACH_IMAGE_BUTTON)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.memo_edit_attach_image)
                    )
                }
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    val hasContent = uiState.title.isNotBlank() || uiState.body.isNotBlank()
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.share_memo)) },
                        onClick = {
                            menuExpanded = false
                            onShareMemo()
                        },
                        enabled = hasContent
                    )
                }
            }
            if (uiState.memoId != null && canEdit) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_memo)
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun MemoEditScreenPreview() {
    LiteMemoTheme {
        MemoEditScreen(
            uiState = MemoEditUiState(
                isLoading = false,
                memoId = "memo-1",
                title = "買い物リスト",
                body = "卵、牛乳、コーヒー豆。帰りに駅前で買う。",
                isModified = true
            ),
            onTitleChange = {},
            onBodyChange = {},
            onTagToggle = {},
            onDelete = {},
            onBackRequest = {},
            onRetry = {},
            onRetryTags = {},
            onAttachImageRequest = {},
            onImageRemove = {},
            onShareMemo = {}
        )
    }
}
