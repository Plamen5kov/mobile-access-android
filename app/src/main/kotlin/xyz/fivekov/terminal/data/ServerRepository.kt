package xyz.fivekov.terminal.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ServerRepository(private val prefs: AppPreferences) {

    private val storage = prefs.secure

    fun getAll(): List<ServerConfig> {
        val json = storage.getString(KEY_SERVERS, "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ServerConfig(
                id = obj.getString("id"),
                name = obj.optString("name", ""),
                hostname = obj.getString("hostname"),
                port = obj.optInt("port", 22),
                username = obj.getString("username"),
                tmuxSession = obj.optString("tmuxSession", ""),
            )
        }
    }

    fun get(id: String): ServerConfig? = getAll().find { it.id == id }

    fun getActive(): ServerConfig? {
        val activeId = prefs.activeServerId ?: return getAll().firstOrNull()
        return get(activeId) ?: getAll().firstOrNull()
    }

    fun save(config: ServerConfig): ServerConfig {
        val withId = if (config.id.isBlank()) {
            config.copy(id = UUID.randomUUID().toString())
        } else {
            config
        }
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == withId.id }
        if (index >= 0) {
            all[index] = withId
        } else {
            all.add(withId)
        }
        persist(all)
        return withId
    }

    fun delete(id: String) {
        val all = getAll().filter { it.id != id }
        persist(all)
        if (prefs.activeServerId == id) {
            prefs.activeServerId = all.firstOrNull()?.id
        }
    }

    private fun persist(servers: List<ServerConfig>) {
        val array = JSONArray()
        for (s in servers) {
            array.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("hostname", s.hostname)
                put("port", s.port)
                put("username", s.username)
                put("tmuxSession", s.tmuxSession)
            })
        }
        storage.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    companion object {
        private const val KEY_SERVERS = "saved_servers"
    }
}
