package xyz.fivekov.terminal.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        prefs.secure.edit().clear().commit()
    }

    @Test
    fun `activeServerId defaults to null`() {
        assertNull(prefs.activeServerId)
    }

    @Test
    fun `activeServerId persists value`() {
        prefs.activeServerId = "server-123"
        assertEquals("server-123", prefs.activeServerId)
    }

    @Test
    fun `theme defaults to dark`() {
        assertEquals("dark", prefs.theme)
    }

    @Test
    fun `theme persists light`() {
        prefs.theme = "light"
        assertEquals("light", prefs.theme)
    }
}
