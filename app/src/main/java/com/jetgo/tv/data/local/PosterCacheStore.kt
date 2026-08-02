package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.posterCacheDataStore by preferencesDataStore(name = "poster_cache")

/** Marca especial: significa "ya se buscó este título y no se encontró nada" (evita repetir la búsqueda) */
private const val NOT_FOUND = "__NOT_FOUND__"

class PosterCacheStore(private val context: Context) {

    private fun keyFor(title: String) = stringPreferencesKey("poster_${title.trim().lowercase()}")

    /** null = todavía no se buscó nunca; "" = ya se buscó pero no había carátula */
    suspend fun get(title: String): String? {
        val stored = context.posterCacheDataStore.data.first()[keyFor(title)] ?: return null
        return if (stored == NOT_FOUND) "" else stored
    }

    suspend fun save(title: String, posterUrl: String?) {
        context.posterCacheDataStore.edit { prefs ->
            prefs[keyFor(title)] = posterUrl ?: NOT_FOUND
        }
    }
}
