package io.github.dumahuch.pogodaapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.widget.RemoteViews
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Рисует виджеты погоды и обновляет их данные (0.8.8). Данные берутся из прогноза, который сохранило приложение для
 * места «там, где вы» (тот же файл, что у уведомления). Фоновая задача ([refresh]) приносит свежий прогноз, если сохранённому
 * больше [FRESH]; [renderAll] только перерисовывает по тому, что на диске (смена размера, открытие и закрытие приложения).
 */
class WidgetUpdater(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, WeatherWidgetProvider::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    fun widgetIds(): IntArray = manager.getAppWidgetIds(component)

    fun hasWidgets(): Boolean = widgetIds().isNotEmpty()

    /** Фоновая задача: свежий прогноз, если сохранённый устарел, и перерисовка всех виджетов. */
    suspend fun refresh() {
        if (!hasWidgets()) return
        val entry = cache.load() ?: return
        if (Duration.between(entry.savedAt, Instant.now()) > FRESH) {
            val client = OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}")
            val raw = client.fetchRaw(entry.place.latitude, entry.place.longitude)
            try {
                OpenMeteoParser.parse(raw)
                cache.save(ForecastCache.Entry(Instant.now(), entry.place, raw))
            } catch (e: ForecastException) {
                Log.w(TAG, "ответ прогноза не разобран, виджет остаётся по прежнему", e)
            }
        }
        renderAll()
    }

    /** Перерисовка по сохранённому прогнозу, без сети. Без сохранённого прогноза виджет пишет «Нет данных». */
    fun renderAll(ids: IntArray = widgetIds()) {
        if (ids.isEmpty()) return
        val entry = cache.load()
        val forecast = entry?.let { parse(it.raw) }
        val model = if (entry != null && forecast != null) WidgetModels.of(entry.place, forecast, Instant.now()) else null
        for (id in ids) render(id, model)
    }

    private fun parse(raw: String): Forecast? = try {
        OpenMeteoParser.parse(raw)
    } catch (e: ForecastException) {
        null
    }

    private fun render(id: Int, model: WidgetModel?) {
        val options = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthDp = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_DP)
        val heightDp = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, DEFAULT_DP)
        val layout = WidgetLayouts.pick(widthDp, heightDp)
        val density = context.resources.displayMetrics.density
        var widthPx = (widthDp * density).roundToInt().coerceAtLeast(1)
        var heightPx = (heightDp * density).roundToInt().coerceAtLeast(1)
        // Картинка в виджете ограничена памятью: сверх предела рисуем мельче, система растянет.
        val bytes = widthPx.toLong() * heightPx * BYTES_PER_PIXEL
        if (bytes > MAX_BITMAP_BYTES) {
            val shrink = sqrt(MAX_BITMAP_BYTES.toDouble() / bytes)
            widthPx = (widthPx * shrink).roundToInt().coerceAtLeast(1)
            heightPx = (heightPx * shrink).roundToInt().coerceAtLeast(1)
        }
        val settings = AppSettings(PreferencesSettingsStorage(context))
        val bitmap = WidgetRenderer(context).render(model, layout, widthPx, heightPx, WidgetStyleStore(context).read(id), settings.iconStyle.value)
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        views.setImageViewBitmap(R.id.widget_image, bitmap)
        views.setContentDescription(R.id.widget_image, description(model))
        openApp()?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
        manager.updateAppWidget(id, views)
    }

    /** Текст для чтения с экрана: «Ганновер, 7°, облачно». */
    private fun description(model: WidgetModel?): String {
        if (model == null) return context.getString(R.string.widget_no_data)
        val condition = context.getString(if (model.drizzle) R.string.wx_drizzle else conditionNameRes(model.condition))
        return context.getString(R.string.widget_description_reading, model.placeName.orEmpty(), temperatureText(model.temperature), condition)
    }

    /** Нажатие открывает приложение так же, как значок на рабочем столе. */
    private fun openApp(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val TAG = "PogodaWidget"
        private val FRESH: Duration = Duration.ofMinutes(10)
        private const val DEFAULT_DP = 110
        private const val BYTES_PER_PIXEL = 4
        private const val MAX_BITMAP_BYTES = 6_000_000L

        /** Перерисовать виджеты в фоне и не ронять приложение, если что-то пошло не так. */
        fun renderInBackground(context: Context, ids: IntArray? = null, done: () -> Unit = {}) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val updater = WidgetUpdater(app)
                    updater.renderAll(ids ?: updater.widgetIds())
                } catch (e: Exception) {
                    Log.w(TAG, "виджет не перерисован", e)
                } finally {
                    done()
                }
            }
        }
    }
}
