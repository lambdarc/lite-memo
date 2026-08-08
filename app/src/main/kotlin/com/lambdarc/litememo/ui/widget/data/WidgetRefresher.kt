package com.lambdarc.litememo.ui.widget.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.lambdarc.litememo.ui.widget.recent.RecentMemosWidget

object WidgetRefresher {

    suspend fun refreshLists(context: Context) {
        val widgetIds = GlanceAppWidgetManager(context)
            .getGlanceIds(RecentMemosWidget::class.java)
        if (widgetIds.isEmpty()) return
        RecentMemosWidget().updateAll(context)
    }

}
