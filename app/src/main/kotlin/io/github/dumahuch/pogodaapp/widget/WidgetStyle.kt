package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle

/**
 * Фон виджета (решение Димы 14.11.2026): «Небо» (нарисованное небо «Ди» по погоде и времени суток, затемнение выбирает человек),
 * «Стекло» (прозрачность выбирается) или «Без фона».
 */
enum class WidgetBackground(val key: String) {
    Sky("sky"),
    Glass("glass"),
    None("none");

    companion object {
        fun fromKey(key: String?): WidgetBackground = entries.firstOrNull { it.key == key } ?: Sky
    }
}

/**
 * Сколько сведений показывает виджет (слайдер из трёх делений, решение Димы 14.11.2026): «Минимум» температура (и город там,
 * где есть место), «Обычно» ещё фраза про осадки и максимум с минимумом, «Подробно» ещё «Ощущается как», ветер, влажность,
 * осадки и дни. В малых размерах «Подробно» то же, что «Обычно».
 */
enum class WidgetDetail { Minimum, Normal, Full;

    companion object {
        fun fromIndex(index: Int): WidgetDetail = entries.getOrElse(index) { Normal }
    }
}

/**
 * Вид одного виджета: [background], [opacity] (процент, насколько фон-стекло непрозрачен), [shade] (процент затемнения неба),
 * [detail] и [icons] (набор значков: пусто значит тот, что выбран в настройках приложения). На небе значка нет: облака,
 * солнце и луна уже на небе.
 */
data class WidgetStyle(
    val background: WidgetBackground = WidgetBackground.Sky,
    val opacity: Int = DEFAULT_OPACITY,
    val shade: Int = DEFAULT_SHADE,
    val detail: WidgetDetail = WidgetDetail.Normal,
    val icons: WeatherIconStyle? = null,
) {
    companion object {
        const val DEFAULT_OPACITY = 40
        const val MIN_OPACITY = 10
        const val MAX_OPACITY = 90
        const val DEFAULT_SHADE = 40
        const val MIN_SHADE = 0
        const val MAX_SHADE = 80
    }
}

/** Вид каждого виджета по номеру, в файле SharedPreferences папки приложения (в облако не уходит, копирование выключено). */
class WidgetStyleStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(widgetId: Int): WidgetStyle = WidgetStyle(
        background = WidgetBackground.fromKey(preferences.getString("bg_$widgetId", null)),
        opacity = preferences.getInt("opacity_$widgetId", WidgetStyle.DEFAULT_OPACITY)
            .coerceIn(WidgetStyle.MIN_OPACITY, WidgetStyle.MAX_OPACITY),
        shade = preferences.getInt("shade_$widgetId", WidgetStyle.DEFAULT_SHADE)
            .coerceIn(WidgetStyle.MIN_SHADE, WidgetStyle.MAX_SHADE),
        detail = WidgetDetail.fromIndex(preferences.getInt("detail_$widgetId", WidgetDetail.Normal.ordinal)),
        icons = preferences.getString("icons_$widgetId", null)?.let { key -> WeatherIconStyle.entries.firstOrNull { it.key == key } },
    )

    fun write(widgetId: Int, style: WidgetStyle) {
        val editor = preferences.edit()
            .putString("bg_$widgetId", style.background.key)
            .putInt("opacity_$widgetId", style.opacity)
            .putInt("shade_$widgetId", style.shade)
            .putInt("detail_$widgetId", style.detail.ordinal)
        val icons = style.icons
        if (icons == null) editor.remove("icons_$widgetId") else editor.putString("icons_$widgetId", icons.key)
        editor.apply()
    }

    fun remove(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (id in widgetIds) editor.remove("bg_$id").remove("opacity_$id").remove("shade_$id").remove("detail_$id").remove("icons_$id")
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "widgets"
    }
}
