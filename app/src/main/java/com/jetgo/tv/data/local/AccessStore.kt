package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accessDataStore by preferencesDataStore(name = "access_store")

class AccessStore(private val context: Context) {

    companion object {
        private val KEY_CODE = stringPreferencesKey("access_code")
        private val KEY_LAST_FORCE_CLOSE_SIGNAL = longPreferencesKey("last_force_close_signal")
    }

    /** El código guardado localmente, o null si nunca se ingresó uno válido */
    val savedCode: Flow<String?> = context.accessDataStore.data.map { it[KEY_CODE] }

    suspend fun saveCode(code: String) {
        context.accessDataStore.edit { it[KEY_CODE] = code }
    }

    suspend fun clear() {
        context.accessDataStore.edit { it.clear() }
    }

    /** Recuerda hasta qué señal de "cerrar la app" ya se atendió, para no repetir el cierre
     *  una y otra vez por la misma renovación cada vez que la app se vuelve a abrir. */
    suspend fun getLastForceCloseSignal(): Long =
        context.accessDataStore.data.first()[KEY_LAST_FORCE_CLOSE_SIGNAL] ?: 0L

    suspend fun saveLastForceCloseSignal(millis: Long) {
        context.accessDataStore.edit { it[KEY_LAST_FORCE_CLOSE_SIGNAL] = millis }
    }
}
