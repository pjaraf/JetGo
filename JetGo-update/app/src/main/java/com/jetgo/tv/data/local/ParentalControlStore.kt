package com.jetgo.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.parentalDataStore by preferencesDataStore(name = "parental_control")

class ParentalControlStore(private val context: Context) {
    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_PIN = stringPreferencesKey("pin")

    suspend fun isEnabled(): Boolean = context.parentalDataStore.data.first()[KEY_ENABLED] ?: false

    suspend fun getPin(): String? = context.parentalDataStore.data.first()[KEY_PIN]

    suspend fun setEnabled(enabled: Boolean, pin: String? = null) {
        context.parentalDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
            if (pin != null) prefs[KEY_PIN] = pin
        }
    }
}
