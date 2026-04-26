package app.paperkeep.feature.scanner.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropCoordinateMapperTest {

    @Test
    fun `build mapper centers image for contain fit`() {
        val mapper = buildCropCoordinateMapper(
            imageWidth = 1000,
            imageHeight = 500,
            viewportWidth = 1000,
            viewportHeight = 1000,
        )

        assertEquals(1f, mapper.scale, 0.0001f)
        assertEquals(0f, mapper.offsetX, 0.0001f)
        assertEquals(250f, mapper.offsetY, 0.0001f)
    }

    @Test
    fun `screen to image maps with offset and scale`() {
        val mapper = buildCropCoordinateMapper(
            imageWidth = 1000,
            imageHeight = 500,
            viewportWidth = 500,
            viewportHeight = 500,
        )

        // Image is drawn at 500x250 with 125px top offset.
        val imagePoint = mapper.screenToImage(
            x = 250f,
            y = 250f,
            imageWidth = 999f,
            imageHeight = 499f,
        )

        assertEquals(500f, imagePoint.x, 0.5f)
        assertEquals(250f, imagePoint.y, 0.5f)
    }

    @Test
    fun `screen to image clamps outside viewport`() {
        val mapper = buildCropCoordinateMapper(
            imageWidth = 400,
            imageHeight = 400,
            viewportWidth = 200,
            viewportHeight = 200,
        )

        val imagePoint = mapper.screenToImage(
            x = -50f,
            y = 500f,
            imageWidth = 399f,
            imageHeight = 399f,
        )

        assertTrue(imagePoint.x in 0f..399f)
        assertTrue(imagePoint.y in 0f..399f)
        assertEquals(0f, imagePoint.x, 0.0001f)
        assertEquals(399f, imagePoint.y, 0.0001f)
    }
}
