package xyz.fivekov.terminal.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerRepositoryTest {

    private lateinit var prefs: AppPreferences
    private lateinit var repo: ServerRepository

    @Before
    fun setup() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        prefs.secure.edit().clear().commit()
        repo = ServerRepository(prefs)
    }

    @Test
    fun `getAll returns empty list when no servers stored`() {
        assertEquals(0, repo.getAll().size)
    }

    @Test
    fun `save assigns id to new server`() {
        val config = ServerConfig(
            id = "",
            name = "test",
            hostname = "example.com",
            port = 22,
            username = "user",
        )
        val saved = repo.save(config)
        assertTrue(saved.id.isNotBlank())
    }

    @Test
    fun `save persists and getAll retrieves`() {
        val config = ServerConfig(
            id = "",
            name = "My Server",
            hostname = "192.168.1.100",
            port = 2222,
            username = "admin",
        )
        repo.save(config)

        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("My Server", all[0].name)
        assertEquals("192.168.1.100", all[0].hostname)
        assertEquals(2222, all[0].port)
        assertEquals("admin", all[0].username)
    }

    @Test
    fun `save preserves id when updating`() {
        val config = ServerConfig(
            id = "fixed-id",
            name = "test",
            hostname = "example.com",
            port = 22,
            username = "user",
        )
        val saved = repo.save(config)
        assertEquals("fixed-id", saved.id)
    }

    @Test
    fun `save updates existing server by id`() {
        val saved = repo.save(ServerConfig("", "v1", "host1.com", 22, "user1"))
        repo.save(saved.copy(name = "v2", hostname = "host2.com"))

        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("v2", all[0].name)
        assertEquals("host2.com", all[0].hostname)
    }

    @Test
    fun `save multiple servers`() {
        repo.save(ServerConfig("", "Server A", "host-a.com", 22, "alice"))
        repo.save(ServerConfig("", "Server B", "host-b.com", 22, "bob"))

        val all = repo.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `get by id returns correct server`() {
        val saved = repo.save(ServerConfig("", "target", "target.com", 22, "user"))
        repo.save(ServerConfig("", "other", "other.com", 22, "user"))

        val found = repo.get(saved.id)
        assertNotNull(found)
        assertEquals("target", found!!.name)
    }

    @Test
    fun `get by id returns null for missing id`() {
        assertNull(repo.get("nonexistent"))
    }

    @Test
    fun `delete removes server`() {
        val saved = repo.save(ServerConfig("", "to-delete", "host.com", 22, "user"))
        assertEquals(1, repo.getAll().size)

        repo.delete(saved.id)
        assertEquals(0, repo.getAll().size)
    }

    @Test
    fun `delete clears activeServerId when deleting active server`() {
        val saved = repo.save(ServerConfig("", "active", "host.com", 22, "user"))
        prefs.activeServerId = saved.id

        repo.delete(saved.id)
        // activeServerId should be cleared (or set to next available)
        assertTrue(prefs.activeServerId == null || repo.get(prefs.activeServerId!!) != null)
    }

    @Test
    fun `getActive returns first server when no active set`() {
        repo.save(ServerConfig("", "first", "first.com", 22, "user"))
        repo.save(ServerConfig("", "second", "second.com", 22, "user"))

        val active = repo.getActive()
        assertNotNull(active)
        assertEquals("first", active!!.name)
    }

    @Test
    fun `getActive returns designated active server`() {
        repo.save(ServerConfig("", "first", "first.com", 22, "user"))
        val second = repo.save(ServerConfig("", "second", "second.com", 22, "user"))
        prefs.activeServerId = second.id

        val active = repo.getActive()
        assertNotNull(active)
        assertEquals("second", active!!.name)
    }

    @Test
    fun `getActive returns null when no servers`() {
        assertNull(repo.getActive())
    }

    @Test
    fun `displayName falls back to user at host`() {
        val config = ServerConfig("1", "", "example.com", 22, "admin")
        assertEquals("admin@example.com", config.displayName)
    }

    @Test
    fun `displayName uses name when set`() {
        val config = ServerConfig("1", "My Server", "example.com", 22, "admin")
        assertEquals("My Server", config.displayName)
    }
}
