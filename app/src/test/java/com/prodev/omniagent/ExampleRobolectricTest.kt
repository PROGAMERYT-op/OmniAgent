package com.prodev.omniagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.prodev.omniagent.engine.PrivacyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OmniAgent AI", appName)
    }

    @Test
    fun `privacy engine detects sensitive actions`() {
        assertTrue(PrivacyEngine.isActionSensitive("Click Confirm Payment"))
        assertTrue(PrivacyEngine.isActionSensitive("Delete all user data"))
        assertTrue(PrivacyEngine.isActionSensitive("Checkout and pay $50"))
        assertFalse(PrivacyEngine.isActionSensitive("Open YouTube app"))
        assertFalse(PrivacyEngine.isActionSensitive("Search for lofi music"))
    }

    @Test
    fun `privacy engine masks sensitive regions with proportional scaling`() {
        // Create a 500x1000 bitmap representing a 0.5x captured screenshot
        val bitmap = android.graphics.Bitmap.createBitmap(500, 1000, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Define a sensitive area on the 1000x2000 physical screen:
        // Physical: left=200, top=400, right=400, bottom=600
        // On 500x1000 bitmap (0.5x scale), this should map to:
        // Scaled: left=100, top=200, right=200, bottom=300
        val physicalBounds = listOf(android.graphics.Rect(200, 400, 400, 600))
        val masked = PrivacyEngine.maskSensitiveRegions(bitmap, physicalBounds, referenceScreenWidth = 1000, referenceScreenHeight = 2000)

        // The point (150, 250) is right in the center of the scaled sensitive area and MUST be masked (black)
        val centerPixel = masked.getPixel(150, 250)
        assertEquals(android.graphics.Color.BLACK, centerPixel)

        // A point outside the sensitive area (e.g. 50, 50) MUST NOT be masked (not black)
        val outsidePixel = masked.getPixel(50, 50)
        org.junit.Assert.assertNotEquals(android.graphics.Color.BLACK, outsidePixel)
    }

    @Test
    fun `screen capture manager provides real screen dimensions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (width, height) = com.prodev.omniagent.engine.ScreenCaptureManager.getRealScreenDimensions(context)
        assertTrue(width > 0)
        assertTrue(height > 0)
    }
}
