package com.therefore.logic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK,
    /** Pure black backgrounds, for OLED screens. */
    BLACK }

/** The accent color the app is painted in. */
enum class AccentTheme(val label: String) { BLUE("Blue"), TEAL("Teal"), PURPLE("Purple"), ROSE("Rose"), AMBER("Amber"), FOREST("Forest"), GRAPHITE("Graphite") }

/** Text size, applied as a multiplier on top of the phone's own font size. */
enum class TextSize(val label: String, val scale: Float) { NORMAL("Normal", 1f), LARGE("Large", 1.15f), LARGER("Larger", 1.3f) }

data class Settings(
    /** Show formulas as ~ & v -> instead of ¬ ∧ ∨ →. */
    val plainText: Boolean = false,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminder: Boolean = false,
    /** Minutes after midnight. */
    val reminderAt: Int = 19 * 60,
    val accent: AccentTheme = AccentTheme.BLUE,
    val textSize: TextSize = TextSize.NORMAL,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val plain = booleanPreferencesKey("plain_text")
        val haptics = booleanPreferencesKey("haptics")
        val theme = stringPreferencesKey("theme")
        val reminder = booleanPreferencesKey("reminder")
        val reminderAt = intPreferencesKey("reminder_at")
        val accent = stringPreferencesKey("accent")
        val textSize = stringPreferencesKey("text_size")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            plainText = p[Keys.plain] ?: false,
            haptics = p[Keys.haptics] ?: true,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            reminder = p[Keys.reminder] ?: false,
            reminderAt = p[Keys.reminderAt] ?: (19 * 60),
            accent = p[Keys.accent]?.let { runCatching { AccentTheme.valueOf(it) }.getOrNull() } ?: AccentTheme.BLUE,
            textSize = p[Keys.textSize]?.let { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: TextSize.NORMAL,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPlainText(v: Boolean) = context.dataStore.edit { it[Keys.plain] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.haptics] = v }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setAccent(v: AccentTheme) = context.dataStore.edit { it[Keys.accent] = v.name }
    suspend fun setTextSize(v: TextSize) = context.dataStore.edit { it[Keys.textSize] = v.name }
    suspend fun setReminder(on: Boolean, at: Int) = context.dataStore.edit {
        it[Keys.reminder] = on
        it[Keys.reminderAt] = at
    }
}
