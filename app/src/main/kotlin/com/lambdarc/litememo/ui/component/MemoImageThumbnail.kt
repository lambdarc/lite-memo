package com.lambdarc.litememo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lambdarc.litememo.R
import java.io.File

@Composable
fun MemoImageThumbnail(imagePath: String, modifier: Modifier = Modifier, testTag: String? = null) {
    var isThumbnailError by remember(imagePath) { mutableStateOf(false) }
    val tagModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(tagModifier),
        contentAlignment = Alignment.Center
    ) {
        if (isThumbnailError) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = stringResource(R.string.attached_image_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = File(imagePath),
                contentDescription = stringResource(R.string.attached_image_description),
                contentScale = ContentScale.Crop,
                onError = {
                    isThumbnailError = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
