package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.sky.DiMaps
import io.github.dumahuch.pogodaapp.sky.DiRaster
import io.github.dumahuch.pogodaapp.sky.SkyDrift
import io.github.dumahuch.pogodaapp.sky.SkySampler
import io.github.dumahuch.pogodaapp.sky.SkyThemeDriver
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.widget.WidgetModels
import io.github.dumahuch.pogodaapp.widget.WidgetSky
import io.github.dumahuch.pogodaapp.widget.widgetSkyState
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Снимок неба из фона (решение Димы 14.11.2026): фоновая задача, которая и так обновляет виджет и уведомление, пишет и снимок
 * для следующего запуска приложения. Так после долгого перерыва приложение открывается со снимком «сейчас», а не вчерашнего вечера,
 * и между снимком и настоящим небом нет скачка. Небо считается по сохранённому прогнозу, как на главном экране; подложек под
 * блоками в таком снимке нет (приложение не открыто, границ блоков не знает).
 */
internal class SkyBackgroundSnapshot(private val context: Context) {

    /** Пишет снимок по сохранённому прогнозу места «там, где вы». Свежий снимок приложения (записан при открытом экране) не трогает. */
    fun refresh(now: Instant = Instant.now()) {
        if (!isDue(SkySnapshotStorage.ageMillis(context, now.toEpochMilli()))) return
        val entry = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE)).load() ?: return
        val forecast = try {
            OpenMeteoParser.parse(entry.raw)
        } catch (e: ForecastException) {
            return
        }
        val model = WidgetModels.of(entry.place, forecast, now)
        val raster = loadRaster() ?: return
        val state = widgetSkyState(model)
        val driver = SkyThemeDriver(SkySampler(raster))
        val scores = driver.measure(state, SkyDrift.position(state.pan, SkyDrift.START_PHASE), 0.0)
        val palette = driver.palette(scores, INSTANT_SECONDS).toUi()

        val (width, height) = SkySnapshotStorage.size(context) ?: (WIDTH to DEFAULT_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val scale = width / LAYOUT_WIDTH
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            WidgetSky(context, screen = true).draw(canvas, LAYOUT_WIDTH, height / scale, scale, model)
            SkySnapshotStorage.save(context, bitmap, palette)
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadRaster(): DiRaster? = try {
        context.assets.open(DiMaps.ASSET_PATH).use { DiRaster(DiMaps.read(it)) }
    } catch (e: IOException) {
        Log.w(TAG, "Не удалось прочитать карты яркости неба", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Файл карт яркости неба испорчен", e)
        null
    }

    companion object {
        /** Нужен ли фоновый снимок: снимка нет или он старше [KEEP_APP_SNAPSHOT_MILLIS] (записанный открытым приложением моложе лучше, на нём есть подложки). */
        fun isDue(ageMillis: Long?): Boolean = ageMillis == null || ageMillis >= KEEP_APP_SNAPSHOT_MILLIS

        private const val TAG = "PogodaSky"

        /** Моложе этого возраста снимок открытого приложения не заменяется. */
        private const val KEEP_APP_SNAPSHOT_MILLIS = 20 * 60_000L

        /** Как в приложении ([SNAPSHOT_WIDTH] в SkyHost): ширина снимка, высота по умолчанию для телефона 1:2,17. */
        private const val WIDTH = 96
        private const val DEFAULT_HEIGHT = 208
        private const val LAYOUT_WIDTH = 480f

        /** Палитра сразу в установившемся виде, как при выключенных анимациях. */
        private const val INSTANT_SECONDS = 10.0
    }
}
