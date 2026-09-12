package kr.dwas.dwas_EQ.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    KOREAN("ko-KR"),
    ENGLISH("en-US"),
    JAPANESE("ja-JP"),
    TRADITIONAL_CHINESE("zh-Hant"),
    TAIWAN("zh-TW"),
    RUSSIAN("ru-RU"),
    VIETNAMESE("vi-VN"),
}

object AppLocaleController {
    private const val PREFS = "dwas_eq_locale"
    private const val KEY_LANGUAGE = "language_mode"

    fun current(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, null)
        if (stored != null) return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.SYSTEM)
        val legacy = context.getSystemService(LocaleManager::class.java).applicationLocales
        val migrated = if (legacy.isEmpty) AppLanguage.SYSTEM else languageFromTag(legacy[0]?.toLanguageTag().orEmpty())
        prefs.edit().putString(KEY_LANGUAGE, migrated.name).apply()
        return migrated
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.name).apply()
        apply(context, language)
    }

    fun sync(context: Context) {
        apply(context, current(context))
    }

    fun normalizeSystemLocaleTag(rawTag: String): String {
        val stripped = rawTag.trim().replace(Regex("(?i)zhzh$"), "")
        if (stripped.isBlank()) return "en-US"
        val locale = Locale.forLanguageTag(stripped)
        return when (locale.language.lowercase(Locale.ROOT)) {
            "ko" -> "ko-KR"
            "en" -> if (locale.country.isNotBlank()) "en-${locale.country.uppercase(Locale.ROOT)}" else "en-US"
            "ja" -> "ja-JP"
            "ru" -> "ru-RU"
            "vi" -> "vi-VN"
            "zh" -> when {
                locale.country.equals("TW", true) -> "zh-TW"
                locale.script.equals("Hant", true) -> "zh-Hant"
                else -> "zh-Hant"
            }
            else -> "en-US"
        }
    }

    fun effectiveLanguage(context: Context): AppLanguage {
        val current = current(context)
        if (current != AppLanguage.SYSTEM) return current
        val systemLocales = context.getSystemService(LocaleManager::class.java).systemLocales
        val raw = if (systemLocales.isEmpty) Locale.getDefault().toLanguageTag() else systemLocales[0]?.toLanguageTag().orEmpty()
        return languageFromTag(normalizeSystemLocaleTag(raw))
    }

    fun isKorean(context: Context): Boolean = effectiveLanguage(context) == AppLanguage.KOREAN

    private fun apply(context: Context, language: AppLanguage) {
        val tag = if (language == AppLanguage.SYSTEM) {
            val systemLocales = context.getSystemService(LocaleManager::class.java).systemLocales
            val raw = if (systemLocales.isEmpty) Locale.getDefault().toLanguageTag() else systemLocales[0]?.toLanguageTag().orEmpty()
            normalizeSystemLocaleTag(raw)
        } else {
            language.languageTag.orEmpty()
        }
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    private fun languageFromTag(tag: String): AppLanguage {
        val normalized = normalizeSystemLocaleTag(tag)
        val locale = Locale.forLanguageTag(normalized)
        return when (locale.language.lowercase(Locale.ROOT)) {
            "ko" -> AppLanguage.KOREAN
            "ja" -> AppLanguage.JAPANESE
            "ru" -> AppLanguage.RUSSIAN
            "vi" -> AppLanguage.VIETNAMESE
            "zh" -> if (locale.country.equals("TW", true)) AppLanguage.TAIWAN else AppLanguage.TRADITIONAL_CHINESE
            else -> AppLanguage.ENGLISH
        }
    }
}
