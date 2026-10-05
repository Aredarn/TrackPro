package com.example.trackpro.managerClasses.utilities

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language choice, independent of the phone's: follow the system, or force
 * English or Hungarian. Stored as a tag so a new language is one more constant.
 *
 * Applied in two places. The activity wraps its base context, so everything drawn reads
 * the chosen language; the application's resources are updated too, so text produced
 * outside a screen (the recording notification, sync messages) follows it as well.
 * Changing it recreates the activity - navigation state survives that.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val HUNGARIAN = "hu"

    private const val PREFS = "language_prefs"
    private const val KEY = "language"

    fun stored(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun store(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    private fun localeFor(tag: String): Locale = when (tag) {
        ENGLISH -> Locale.ENGLISH
        HUNGARIAN -> Locale("hu", "HU")
        else -> Resources.getSystem().configuration.locales[0]
    }

    private fun configured(base: Configuration, locale: Locale): Configuration =
        Configuration(base).apply { setLocales(LocaleList(locale)) }

    /** For Activity.attachBaseContext. */
    fun wrap(base: Context): Context {
        val locale = localeFor(stored(base))
        // Date and number formatting reads the default locale, not the resources'.
        Locale.setDefault(locale)
        return base.createConfigurationContext(configured(base.resources.configuration, locale))
    }

    /** Brings the application's resources in line with the stored choice. */
    @Suppress("DEPRECATION")
    fun applyTo(context: Context) {
        val locale = localeFor(stored(context))
        Locale.setDefault(locale)
        val res = context.resources
        res.updateConfiguration(configured(res.configuration, locale), res.displayMetrics)
    }
}

/** The activity behind a composition's context, if there is one. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
