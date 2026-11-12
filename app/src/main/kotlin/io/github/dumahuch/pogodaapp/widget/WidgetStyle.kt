package io.github.dumahuch.pogodaapp.widget

import android.content.Context

/** Фон виджета (решение Димы 12.11.2026: стекло или без фона, прозрачность выбирается при добавлении). */
enum class WidgetBackground(val key: String) {
    Glass("glass"),
    None("none");

    companion object {
        fun fromKey(key: String?): WidgetBackground = entries.firstOrNull { it.key == key } ?: Glass
    }
}

/** Вид одного виджета: [background] и [opacity] (процент, насколько фон-стекло непрозрачен). */
data class WidgetStyle(val background: WidgetBackground = WidgetBackground.Glass, val opacity: Int = DEFAULT_OPACITY) {
    companion object {
        const val DEFAULT_OPACITY = 40
        const val MIN_OPACITY = 10
        const val MAX_OPACITY = 90
    }
}

/** Вид каждого виджета по номеру, в файле SharedPreferences папки приложения (в облако не уходит, копирование выключено). */
class WidgetStyleStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(widgetId: Int): WidgetStyle = WidgetStyle(
        background = WidgetBackground.fromKey(preferences.getString("bg_$widgetId", null)),
        opacity = preferences.getInt("opacity_$widgetId", WidgetStyle.DEFAULT_OPACITY)
            .coerceIn(WidgetStyle.MIN_OPACITY, WidgetStyle.MAX_OPACITY),
    )

    fun write(widgetId: Int, style: WidgetStyle) {
        preferences.edit()
            .putString("bg_$widgetId", style.background.key)
            .putInt("opacity_$widgetId", style.opacity)
            .apply()
    }

    fun remove(widgetIds: IntArray) {
        val editor = preferences.edit()
        for (id in widgetIds) editor.remove("bg_$id").remove("opacity_$id")
        editor.apply()
    }

    private companion object {
        const val FILE_NAME = "widgets"
    }
}
