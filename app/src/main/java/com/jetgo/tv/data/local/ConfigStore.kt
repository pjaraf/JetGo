package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jetgo.tv.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "stream_config")

class ConfigStore(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_USER = stringPreferencesKey("username")
        private val KEY_PASS = stringPreferencesKey("password")
        private val KEY_MODE = stringPreferencesKey("mode") // "xtream" o "m3u"
        private val KEY_M3U_URL = stringPreferencesKey("m3u_url")
    }

    val config: Flow<ServerConfig?> = context.dataStore.data.map { prefs ->
        val host = prefs[KEY_HOST] ?: return@map null
        val user = prefs[KEY_USER] ?: return@map null
        val pass = prefs[KEY_PASS] ?: return@map null
        ServerConfig(host, user, pass)
    }

    val mode: Flow<String> = context.dataStore.data.map { it[KEY_MODE] ?: "xtream" }
    val m3uUrl: Flow<String?> = context.dataStore.data.map { it[KEY_M3U_URL] }

    suspend fun saveXtream(host: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_USER] = username
            prefs[KEY_PASS] = password
            prefs[KEY_MODE] = "xtream"
        }
    }

    suspend fun saveM3u(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_M3U_URL] = url
            prefs[KEY_MODE] = "m3u"
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs -> prefs.clear() }
    }
}
