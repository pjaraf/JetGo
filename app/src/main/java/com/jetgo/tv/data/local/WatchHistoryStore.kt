package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.watchHistoryDataStore by preferencesDataStore(name = "watch_history")

data class WatchHistoryEntry(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val type: String, // "MOVIE" o "SERIES"
    val timestamp: Long
)

/**
 * Guarda qué películas/series ha ido viendo el cliente, para mostrarlas en "Seguir viendo".
 * Se actualiza cada vez que arranca a reproducir una (o un capítulo de una serie).
 */
class WatchHistoryStore(private val context: Context) {

    private val KEY = stringPreferencesKey("history_json")
    private val MAX_ENTRIES = 30

    suspend fun record(entry: WatchHistoryEntry) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == entry.id && it.type == entry.type }
        current.add(0, entry)
        val trimmed = current.take(MAX_ENTRIES)
        save(trimmed)
    }

    suspend fun getAll(): List<WatchHistoryEntry> {
        val raw = context.watchHistoryDataStore.data.first()[KEY] ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                WatchHistoryEntry(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
                    type = obj.getString("type"),
                    timestamp = obj.optLong("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun save(entries: List<WatchHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("name", entry.name)
            obj.put("imageUrl", entry.imageUrl ?: "")
            obj.put("type", entry.type)
            obj.put("timestamp", entry.timestamp)
            array.put(obj)
        }
        context.watchHistoryDataStore.edit { prefs -> prefs[KEY] = array.toString() }
    }

    suspend fun remove(id: String, type: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == id && it.type == type }
        save(current)
    }

    suspend fun clear() {
        context.watchHistoryDataStore.edit { prefs -> prefs.remove(KEY) }
    }
}
