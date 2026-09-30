package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.PrivacyEngine
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
}
