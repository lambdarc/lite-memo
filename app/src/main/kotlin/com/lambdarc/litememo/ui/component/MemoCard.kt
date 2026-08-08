package com.lambdarc.litememo.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoCard(
    memo: MemoUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val accentColor = memoAccentColor(memo)
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
            role = Role.Button
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = size.copy(width = 4.dp.toPx())
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = memo.title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = memo.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (memo.tags.isEmpty()) {
                            MemoTag(tag = null)
                        } else {
                            memo.tags.forEach { tag ->
                                MemoTag(tag = tag)
                            }
                        }
                    }
                    Text(
                        text = updatedAtLabel(memo.updatedAtMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            memo.thumbnailPath?.let { thumbnailPath ->
                MemoImageThumbnail(
                    imagePath = thumbnailPath,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 14.dp)
                        .size(56.dp),
                    testTag = MemoCardTestTags.THUMBNAIL
                )
            }
        }
    }
}

@Composable
private fun MemoTag(tag: TagUiModel?) {
    TagChip(tag = tag)
}

@Composable
private fun memoAccentColor(memo: MemoUiModel): Color = memo.tags.firstOrNull()?.toComposeColor()
    ?: MaterialTheme.colorScheme.primary

@Composable
private fun updatedAtLabel(updatedAtMillis: Long): String {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val today = LocalDate.now(zoneId)
    val updatedAt = remember(updatedAtMillis, zoneId) {
        Instant.ofEpochMilli(updatedAtMillis).atZone(zoneId)
    }

    return when (updatedAt.toLocalDate()) {
        today -> timeFormatter.format(updatedAt)
        today.minusDays(1) -> stringResource(R.string.yesterday_label)
        else -> dateFormatter.format(updatedAt)
    }
}
