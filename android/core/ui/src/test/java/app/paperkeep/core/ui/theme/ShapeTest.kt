package app.paperkeep.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Paperkeep shape token spec (§7):
 *   Cards  → large = 20dp rounded
 *   FABs   → extraLarge = 28dp (squircle approximation)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShapeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardShape_is20dpRounded() {
        val expected = RoundedCornerShape(20.dp)
        assertEquals("Card shape must be 20dp per spec §7", expected, CardShape)
    }

    @Test
    fun fabShape_is50percentRounded() {
        val expected = RoundedCornerShape(percent = 50)
        assertEquals("FAB shape must be 50% (squircle/pill) per spec §7", expected, FabShape)
    }

    @Test
    fun sheetShape_hasRoundedTopCornersOnly() {
        val shape = SheetShape as? RoundedCornerShape
        // Bottom corners must be 0, top corners must be non-zero
        assertEquals(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shape)
    }

    @Test
    fun theme_largeShape_matchesCardShape() {
        var largeShape: androidx.compose.ui.graphics.Shape? = null
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                largeShape = MaterialTheme.shapes.large
            }
        }
        composeRule.waitForIdle()
        assertEquals(
            "MaterialTheme.shapes.large must match CardShape (20dp)",
            CardShape,
            largeShape,
        )
    }

    @Test
    fun theme_shapes_areWiredCorrectly() {
        var small: androidx.compose.ui.graphics.Shape? = null
        var medium: androidx.compose.ui.graphics.Shape? = null
        var large: androidx.compose.ui.graphics.Shape? = null
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                small = MaterialTheme.shapes.small
                medium = MaterialTheme.shapes.medium
                large = MaterialTheme.shapes.large
            }
        }
        composeRule.waitForIdle()
        assertEquals(PaperkeepShapes.small, small)
        assertEquals(PaperkeepShapes.medium, medium)
        assertEquals(PaperkeepShapes.large, large)
        // Shape scale must be strictly increasing
        assertNotEquals(small, medium)
        assertNotEquals(medium, large)
    }

    @Test
    fun paperkeepShapes_objectHasFiveSlots() {
        // Verify all five M3 shape slots are set (none null/default)
        val shapes = PaperkeepShapes
        // Each slot is non-null (they're value class wrappers — just verify they exist)
        assertEquals(RoundedCornerShape(4.dp),  shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp),  shapes.small)
        assertEquals(RoundedCornerShape(12.dp), shapes.medium)
        assertEquals(RoundedCornerShape(20.dp), shapes.large)
        assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
    }
}
