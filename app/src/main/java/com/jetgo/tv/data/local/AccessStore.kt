package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accessDataStore by preferencesDataStore(name = "access_store")

class AccessStore(private val context: Context) {

    companion object {
        private val KEY_CODE = stringPreferencesKey("access_code")
    }

    /** El código guardado localmente, o null si nunca se ingresó uno válido */
    val savedCode: Flow<String?> = context.accessDataStore.data.map { it[KEY_CODE] }

    suspend fun saveCode(code: String) {
        context.accessDataStore.edit { it[KEY_CODE] = code }
    }

    suspend fun clear() {
        context.accessDataStore.edit { it.clear() }
    }
}
