package kr.dwas.dwas_EQ.ui

import android.content.Context

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

object AppThemeController {
    private const val PREFS = "dwas_eq_theme"
    private const val KEY_MODE = "theme_mode"

    fun current(context: Context): AppThemeMode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return stored?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.SYSTEM
    }

    fun set(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }
}
