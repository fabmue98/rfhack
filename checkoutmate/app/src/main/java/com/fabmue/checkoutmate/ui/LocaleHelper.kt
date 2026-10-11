package com.fabmue.checkoutmate.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    /** Wraps [context] so resources resolve in [language] ("system" keeps the device locale). */
    fun wrap(context: Context, language: String): Context {
        if (language == "system" || language.isBlank()) return context
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
