package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback_positions")

data class SavedPosition(val positionMs: Long, val durationMs: Long)

/**
 * Guarda en qué minuto quedó cada película/episodio, para poder ofrecer
 * "Seguir viendo" la próxima vez que se abra.
 */
class PlaybackPositionStore(private val context: Context) {

    private fun posKey(contentKey: String) = longPreferencesKey("pos_$contentKey")
    private fun durKey(contentKey: String) = longPreferencesKey("dur_$contentKey")

    suspend fun save(contentKey: String, positionMs: Long, durationMs: Long) {
        context.playbackDataStore.edit { prefs ->
            prefs[posKey(contentKey)] = positionMs
            prefs[durKey(contentKey)] = durationMs
        }
    }

    suspend fun get(contentKey: String): SavedPosition? {
        val prefs = context.playbackDataStore.data.first()
        val pos = prefs[posKey(contentKey)]
        val dur = prefs[durKey(contentKey)]
        return if (pos != null && dur != null && dur > 0) SavedPosition(pos, dur) else null
    }

    suspend fun clear(contentKey: String) {
        context.playbackDataStore.edit { prefs ->
            prefs.remove(posKey(contentKey))
            prefs.remove(durKey(contentKey))
        }
    }
}
