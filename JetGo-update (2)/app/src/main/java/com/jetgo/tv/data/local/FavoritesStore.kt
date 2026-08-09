package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites_store")

class FavoritesStore(private val context: Context) {

    companion object {
        private val KEY_FAVORITES = stringPreferencesKey("favorites_json")
    }

    val favorites: Flow<List<ContentItem>> = context.favoritesDataStore.data.map { prefs ->
        val json = prefs[KEY_FAVORITES] ?: "[]"
        parseFavorites(json)
    }

    suspend fun toggleFavorite(item: ContentItem) {
        context.favoritesDataStore.edit { prefs ->
            val current = parseFavorites(prefs[KEY_FAVORITES] ?: "[]").toMutableList()
            val existingIndex = current.indexOfFirst { it.id == item.id && it.type == item.type }
            if (existingIndex >= 0) {
                current.removeAt(existingIndex)
            } else {
                current.add(item)
            }
            prefs[KEY_FAVORITES] = serializeFavorites(current)
        }
    }

    private fun parseFavorites(json: String): List<ContentItem> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ContentItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                    type = ContentType.valueOf(obj.getString("type")),
                    streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeFavorites(items: List<ContentItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("imageUrl", item.imageUrl ?: "")
            obj.put("type", item.type.name)
            obj.put("streamUrl", item.streamUrl ?: "")
            array.put(obj)
        }
        return array.toString()
    }
}
