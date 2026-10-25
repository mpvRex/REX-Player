package xyz.mpv.rex.utils.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import xyz.mpv.rex.R
import java.util.Locale

data class AppLanguage(
  val code: String, // "" for system default, "en", "ar", "bn-BD", "es", "zh-CN", "zh-TW", etc.
  val nativeName: String, // Dynamic native script name (e.g. "English", "العربية", "বাংলা", "Español", "简体中文", "繁體中文")
  val localizedName: String, // Name in the currently active UI language
)

object LocaleHelper {
  private const val PREF_KEY_APP_LANGUAGE = "app_language"

  // Base fallback tags in desired priority order
  private val primarySupportedTags = listOf(
    "en",
    "ar",
    "bn-BD",
    "es",
    "zh-CN",
    "zh-TW",
  )

  /**
   * Dynamically discovers all languages that have actual mpvRex translations
   * in APK resource assets, automatically generating their native script names
   * without hardcoding and filtering out generic AndroidX/Material library locales.
   */
  fun getSupportedLanguages(context: Context): List<AppLanguage> {
    val currentAppLocale = getCurrentAppLocale(context)
    val candidateTags = linkedSetOf<String>()

    // Add primary tags
    candidateTags.addAll(primarySupportedTags)

    // Discover any additional values-xx translation folders in the APK
    try {
      val assetLocales = context.resources.assets.locales
      assetLocales?.forEach { rawTag ->
        if (rawTag.isNotBlank()) {
          val normalized = normalizeTag(rawTag)
          if (normalized.isNotBlank()) {
            candidateTags.add(normalized)
          }
        }
      }
    } catch (_: Exception) {
      // Fallback
    }

    val result = mutableListOf<AppLanguage>()

    // 1. System default entry
    result.add(
      AppLanguage(
        code = "",
        nativeName = context.getString(R.string.system_default),
        localizedName = context.getString(R.string.system_default),
      ),
    )

    // 2. Only include languages that have real mpvRex string resources
    candidateTags.forEach { tag ->
      if (isAppTranslationAvailable(context, tag)) {
        val locale = getLocaleFromCode(tag)
        val nativeName = locale.getDisplayName(locale).replaceFirstChar { char ->
          if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
        val localizedName = locale.getDisplayName(currentAppLocale).replaceFirstChar { char ->
          if (char.isLowerCase()) char.titlecase(currentAppLocale) else char.toString()
        }

        result.add(
          AppLanguage(
            code = tag,
            nativeName = if (nativeName.isNotBlank()) nativeName else tag,
            localizedName = if (localizedName.isNotBlank()) localizedName else tag,
          ),
        )
      }
    }

    return result
  }

  fun getSavedLanguageCode(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val saved = prefs.getString(PREF_KEY_APP_LANGUAGE, null)
    if (saved != null) return saved

    val appLocales = AppCompatDelegate.getApplicationLocales()
    if (!appLocales.isEmpty) {
      val tag = appLocales.toLanguageTags()
      return mapTagToCode(tag)
    }
    return ""
  }

  fun getCurrentLanguage(context: Context): AppLanguage {
    val currentCode = getSavedLanguageCode(context)
    val languages = getSupportedLanguages(context)
    return languages.firstOrNull { it.code == currentCode } ?: languages.first()
  }

  fun setAppLanguage(context: Context, languageCode: String) {
    // 1. Save to SharedPreferences
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit()
      .putString(PREF_KEY_APP_LANGUAGE, languageCode)
      .apply()

    // 2. Set AppCompatLocales
    if (languageCode.isEmpty() || languageCode == "system") {
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    } else {
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }

    // 3. Set Android 13+ LocaleManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val localeManager = context.getSystemService(LocaleManager::class.java)
      if (languageCode.isEmpty() || languageCode == "system") {
        localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
      } else {
        localeManager?.applicationLocales = LocaleList.forLanguageTags(languageCode)
      }
    }

    // 4. Update resources Configuration directly
    updateLocale(context, languageCode)

    // 5. Recreate activity if applicable
    (context as? Activity)?.recreate()
  }

  fun wrapContext(context: Context): Context {
    val langCode = getSavedLanguageCode(context)
    if (langCode.isEmpty() || langCode == "system") {
      return context
    }
    val locale = getLocaleFromCode(langCode)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return context.createConfigurationContext(config)
  }

  fun updateLocale(context: Context, languageCode: String) {
    val locale = if (languageCode.isEmpty() || languageCode == "system") {
      Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    } else {
      getLocaleFromCode(languageCode)
    }
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
  }

  fun getLocaleFromCode(languageCode: String): Locale {
    return when (languageCode) {
      "bn-BD", "bn_BD", "bn" -> Locale("bn", "BD")
      "zh-CN", "zh_CN", "zh-Hans", "zh" -> Locale.SIMPLIFIED_CHINESE
      "zh-TW", "zh_TW", "zh-Hant", "zh-HK" -> Locale.TRADITIONAL_CHINESE
      "ar" -> Locale("ar")
      "es" -> Locale("es")
      "en" -> Locale.ENGLISH
      else -> {
        val parts = languageCode.replace("-r", "-").split("-", "_")
        if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
      }
    }
  }

  /**
   * Checks if mpvRex's own string resources actually provide translations for this locale
   * (filtering out dozens of system/AndroidX library languages).
   */
  private fun isAppTranslationAvailable(context: Context, tag: String): Boolean {
    if (tag.isEmpty() || tag == "en") return true
    return try {
      val locale = getLocaleFromCode(tag)
      val config = Configuration(context.resources.configuration)
      config.setLocale(locale)
      val localizedContext = context.createConfigurationContext(config)

      // Baseline must be resolved in the app's default English locale, not the
      // currently selected UI language — otherwise, with a non-English app
      // language, untranslated (library) locales that fall back to English
      // "differ" from the baseline and are wrongly listed as available.
      val baseConfig = Configuration(context.resources.configuration)
      baseConfig.setLocale(Locale.ENGLISH)
      val defaultTitle = context
        .createConfigurationContext(baseConfig)
        .resources
        .getString(R.string.pref_appearance_title)

      val localizedTitle = localizedContext.resources.getString(R.string.pref_appearance_title)
      defaultTitle != localizedTitle
    } catch (_: Exception) {
      false
    }
  }

  private fun getCurrentAppLocale(context: Context): Locale {
    val savedCode = getSavedLanguageCode(context)
    return if (savedCode.isNotBlank()) {
      getLocaleFromCode(savedCode)
    } else {
      Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    }
  }

  private fun normalizeTag(tag: String): String {
    val cleaned = tag.trim().replace("_", "-")
    return when {
      cleaned.equals("en", ignoreCase = true) || cleaned.startsWith("en-") -> "en"
      cleaned.equals("ar", ignoreCase = true) || cleaned.startsWith("ar-") -> "ar"
      cleaned.equals("bn", ignoreCase = true) || cleaned.startsWith("bn-") -> "bn-BD"
      cleaned.equals("es", ignoreCase = true) || cleaned.startsWith("es-") -> "es"
      cleaned.contains("Hant", ignoreCase = true) || cleaned.contains("TW", ignoreCase = true) || cleaned.contains("HK", ignoreCase = true) -> "zh-TW"
      cleaned.contains("Hans", ignoreCase = true) || cleaned.contains("CN", ignoreCase = true) || cleaned.equals("zh", ignoreCase = true) -> "zh-CN"
      else -> cleaned.split("-").firstOrNull() ?: ""
    }
  }

  private fun mapTagToCode(tag: String): String {
    return when {
      tag.startsWith("bn") -> "bn-BD"
      tag.startsWith("zh-TW") || tag.startsWith("zh-Hant") || tag.startsWith("zh-HK") -> "zh-TW"
      tag.startsWith("zh") -> "zh-CN"
      tag.startsWith("ar") -> "ar"
      tag.startsWith("es") -> "es"
      tag.startsWith("en") -> "en"
      else -> tag.split(",").firstOrNull() ?: ""
    }
  }
}
