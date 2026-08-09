package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.lastPlayingDataStore by preferencesDataStore(name = "last_playing")

data class LastPlayingItem(
    val itemId: String,
    val itemType: String, // "MOVIE" o "SERIES"
    val name: String,
    val imageUrl: String?,
    val savedAtMs: Long
)

/**
 * Guarda qué película o serie está reproduciéndose EN ESTE MOMENTO (no solo su posición, que
 * ya guarda PlaybackPositionStore, sino CUÁL contenido era). Esto es lo que permite retomar
 * automáticamente "Seguir viendo X" si Android mata el proceso de la app a mitad de la
 * reproducción (algo común en TV Box con poca memoria) y el usuario la vuelve a abrir.
 */
class LastPlayingStore(private val context: Context) {

    private val KEY_ID = stringPreferencesKey("item_id")
    private val KEY_TYPE = stringPreferencesKey("item_type")
    private val KEY_NAME = stringPreferencesKey("item_name")
    private val KEY_IMAGE = stringPreferencesKey("item_image")
    private val KEY_SAVED_AT = longPreferencesKey("saved_at")

    suspend fun save(itemId: String, itemType: String, name: String, imageUrl: String?) {
        context.lastPlayingDataStore.edit { prefs ->
            prefs[KEY_ID] = itemId
            prefs[KEY_TYPE] = itemType
            prefs[KEY_NAME] = name
            prefs[KEY_IMAGE] = imageUrl ?: ""
            prefs[KEY_SAVED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun get(): LastPlayingItem? {
        val prefs = context.lastPlayingDataStore.data.first()
        val id = prefs[KEY_ID] ?: return null
        val type = prefs[KEY_TYPE] ?: return null
        val name = prefs[KEY_NAME] ?: return null
        val savedAt = prefs[KEY_SAVED_AT] ?: return null
        return LastPlayingItem(id, type, name, prefs[KEY_IMAGE]?.takeIf { it.isNotBlank() }, savedAt)
    }

    suspend fun clear() {
        context.lastPlayingDataStore.edit { prefs -> prefs.clear() }
    }
}
