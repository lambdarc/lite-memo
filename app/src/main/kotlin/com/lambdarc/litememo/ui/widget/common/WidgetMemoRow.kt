package com.lambdarc.litememo.ui.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.widget.data.WidgetItem

@Composable
fun WidgetMemoRow(item: WidgetItem, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(actionStartActivity(WidgetLaunchIntents.openMemoIntent(context, item.id)))
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isFavorite) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_star),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(14.dp)
                )
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                text = item.title.ifEmpty { context.getString(R.string.widget_untitled) },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
        }
        if (item.snippet.isNotEmpty()) {
            Text(
                text = item.snippet,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}
