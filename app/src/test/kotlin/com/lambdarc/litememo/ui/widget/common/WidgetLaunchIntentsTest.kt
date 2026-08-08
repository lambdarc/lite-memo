package com.lambdarc.litememo.ui.widget.common

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.navigation.WidgetNavRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetLaunchIntentsTest {

    @Test
    fun normalNewMemoTargetParsesToNewMemoRequest() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = WidgetLaunchIntents.TARGET_NEW_MEMO,
            memoId = null
        )

        // Assert
        assertEquals(WidgetNavRequest.NewMemo, request)
    }

    @Test
    fun normalOpenMemoTargetParsesToOpenMemoRequestWithId() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = WidgetLaunchIntents.TARGET_OPEN_MEMO,
            memoId = "memo-1"
        )

        // Assert
        assertEquals(WidgetNavRequest.OpenMemo(MemoId("memo-1")), request)
    }

    @Test
    fun boundaryOpenMemoTrimsIdAtIntentBoundary() {
        // Act
        // Boundary: raw intent extras are normalized when converted to MemoId
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = WidgetLaunchIntents.TARGET_OPEN_MEMO,
            memoId = " memo-1 "
        )

        // Assert
        assertEquals(WidgetNavRequest.OpenMemo(MemoId("memo-1")), request)
    }

    @Test
    fun errorUnknownActionParsesToNull() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = "com.example.OTHER",
            target = WidgetLaunchIntents.TARGET_NEW_MEMO,
            memoId = null
        )

        // Assert
        assertNull(request)
    }

    @Test
    fun errorUnknownTargetParsesToNull() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = "unknown",
            memoId = null
        )

        // Assert
        assertNull(request)
    }

    @Test
    fun boundaryOpenMemoWithBlankIdParsesToNull() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = WidgetLaunchIntents.TARGET_OPEN_MEMO,
            memoId = "   "
        )

        // Assert
        assertNull(request)
    }

    @Test
    fun boundaryOpenMemoWithNullIdParsesToNull() {
        // Act
        val request = WidgetLaunchIntents.parseWidgetNav(
            action = WidgetLaunchIntents.ACTION_WIDGET_OPEN,
            target = WidgetLaunchIntents.TARGET_OPEN_MEMO,
            memoId = null
        )

        // Assert
        assertNull(request)
    }
}
