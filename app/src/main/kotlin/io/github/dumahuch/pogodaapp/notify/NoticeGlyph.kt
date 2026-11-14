package io.github.dumahuch.pogodaapp.notify

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.home.weatherGlyphBitmap
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlin.math.roundToInt

/** Большой значок погоды для уведомлений в выбранном человеком наборе значков (уведомление о погоде и вечерняя сводка). */
internal fun noticeGlyph(context: Context, condition: Condition, isDay: Boolean, windy: Boolean): Icon {
    val density = context.resources.displayMetrics.density
    val px = (LARGE_ICON_DP * density).roundToInt().coerceIn(64, 256)
    val onLight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
    val style = AppSettings(PreferencesSettingsStorage(context)).iconStyle.value
    return Icon.createWithBitmap(weatherGlyphBitmap(condition, isDay, windy, style, onLight, px))
}

private const val LARGE_ICON_DP = 48f
