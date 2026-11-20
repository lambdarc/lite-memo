package com.lambdarc.litememo.ui.navigation

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.text
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class NavigationArgumentEncoderTest {

    @Test
    fun boundaryMemoEditRouteEncodesSpecialCharactersInMemoId() {
        // Arrange
        val memoId = MemoId("memo/1 ?")

        // Act
        // Boundary: the typed identifier is serialized only at the navigation route boundary
        val route = memoEditRouteWithId(memoId)

        // Assert
        assertEquals("memo_edit?memoId=memo%2F1%20%3F", route)
    }

    @Test
    fun propertyEncodedArgumentDecodesToOriginal() = runTest {
        // Act & Assert
        // Property: any text survives an encode and decode round trip
        checkAll(PropTestConfig(iterations = 200), Arb.text()) { value ->
            val encoded = encodeNavigationArgument(value)
            assertEquals(value, URLDecoder.decode(encoded, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun propertyEncodedArgumentUsesOnlyRouteSafeCharacters() = runTest {
        // Act & Assert
        // Property: the encoded text never contains reserved characters or a plus sign
        checkAll(PropTestConfig(iterations = 200), Arb.text()) { value ->
            val encoded = encodeNavigationArgument(value)
            assertTrue(encoded.all { it in ROUTE_SAFE_CHARACTERS }) { "unsafe: $encoded" }
        }
    }

    private companion object {
        val ROUTE_SAFE_CHARACTERS = (('a'..'z') + ('A'..'Z') + ('0'..'9') + "-_.*%".toList())
            .toSet()
    }
}
