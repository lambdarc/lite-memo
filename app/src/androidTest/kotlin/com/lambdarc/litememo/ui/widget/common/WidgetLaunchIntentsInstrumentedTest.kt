package com.lambdarc.litememo.ui.widget.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.domain.model.value.MemoId
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLaunchIntentsInstrumentedTest {

    @Test
    fun interactionOpenMemoIntentsRemainDistinctForDifferentMemoIds() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstIntent = WidgetLaunchIntents.openMemoIntent(context, MemoId("memo-1"))
        val secondIntent = WidgetLaunchIntents.openMemoIntent(context, MemoId("memo-2"))

        // Act
        // Interaction: unique data URIs prevent PendingIntent conflation between memo rows
        val areEquivalent = firstIntent.filterEquals(secondIntent)

        // Assert
        assertFalse(areEquivalent)
    }
}
