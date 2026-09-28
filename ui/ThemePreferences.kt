package com.mlib.notes.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemePreferences(private val context: Context) {
    private object PreferencesKeys {
        val SELECTED_THEME_INDEX = intPreferencesKey("selected_theme_index")
    }

    val selectedThemeIndexFlow: Flow<Int> = context.themeDataStore.data
        .map { preferences -> preferences[PreferencesKeys.SELECTED_THEME_INDEX] ?: 0 }

    suspend fun updateSelectedThemeIndex(index: Int) {
        context.themeDataStore.edit { it[PreferencesKeys.SELECTED_THEME_INDEX] = index }
    }
}
