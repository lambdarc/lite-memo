package com.lambdarc.litememo.ui.widget.recent

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.theme.WidgetColorProviders
import com.lambdarc.litememo.ui.widget.common.WidgetEmptyState
import com.lambdarc.litememo.ui.widget.common.WidgetLaunchIntents
import com.lambdarc.litememo.ui.widget.common.WidgetMemoRow
import com.lambdarc.litememo.ui.widget.data.WidgetItem
import com.lambdarc.litememo.ui.widget.data.WidgetMemoLoader
import com.lambdarc.litememo.ui.widget.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch

class RecentMemosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val loader = WidgetMemoLoader(entryPoint.observeRecentMemosUseCase())
        val initial = runCatching {
            loader.loadRecent()
        }.getOrElse {
            if (it is CancellationException) throw it else emptyList()
        }
        provideContent {
            val items by remember {
                loader.observeRecent().catch {
                    emit(emptyList())
                }
            }.collectAsState(initial = initial)
            GlanceTheme(colors = WidgetColorProviders) {
                RecentMemosContent(items)
            }
        }
    }

}

@Composable
private fun RecentMemosContent(items: List<WidgetItem>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.widget_recent_label),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_edit),
                contentDescription = context.getString(R.string.widget_new_memo_label),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier
                    .size(22.dp)
                    .clickable(actionStartActivity(WidgetLaunchIntents.newMemoIntent(context)))
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                WidgetEmptyState(context.getString(R.string.widget_recent_empty))
            }
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(items = items, itemId = { it.id.hashCode().toLong() }) { item ->
                    WidgetMemoRow(item)
                }
            }
        }
    }
}
