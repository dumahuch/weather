package io.github.dumahuch.pogodaapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import io.github.dumahuch.pogodaapp.notify.WeatherJobs
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage

/**
 * Единый виджет погоды (0.8.8). Сам ничего не считает: перерисовывает картинку по сохранённому прогнозу ([WidgetUpdater]),
 * свежие данные приносит фоновая задача ([WeatherJobs]), которая работает, пока на рабочем столе есть хотя бы один виджет.
 * Приёмник не экспортируется: события приходят от системы.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, appWidgetIds)
    }

    /** Человек растянул или сжал виджет: раскладка выбирается заново. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        redraw(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetStyleStore(context).remove(appWidgetIds)
    }

    /** Первый виджет на рабочем столе: включается фоновое обновление и сразу просится свежий прогноз. */
    override fun onEnabled(context: Context) {
        WeatherJobs.sync(context, settings(context).notifications.value)
        WeatherJobs.refreshSoon(context)
    }

    /** Последний виджет убран: фоновое обновление остаётся, только если оно нужно уведомлениям. */
    override fun onDisabled(context: Context) {
        WeatherJobs.sync(context, settings(context).notifications.value)
    }

    private fun redraw(context: Context, ids: IntArray) {
        val pending = goAsync()
        WidgetUpdater.renderInBackground(context, ids) { pending.finish() }
    }

    private fun settings(context: Context) = AppSettings(PreferencesSettingsStorage(context))
}
