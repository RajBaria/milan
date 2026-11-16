package com.example.ui.theme

import android.content.Context
import com.example.data.model.AppColorTheme

object ThemePreferences {
    private const val PREFS_NAME = "milan_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode"
    private const val KEY_COLOR_THEME = "app_color_theme"

    fun loadDarkMode(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun saveDarkMode(context: Context, isDark: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, isDark)
            .apply()
    }

    fun loadColorTheme(context: Context): AppColorTheme {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COLOR_THEME, AppColorTheme.PINK.name).orEmpty()
        return AppColorTheme.entries.firstOrNull { it.name == raw } ?: AppColorTheme.PINK
    }

    fun saveColorTheme(context: Context, theme: AppColorTheme) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_THEME, theme.name)
            .apply()
    }
}
