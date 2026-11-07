package io.github.dumahuch.pogodaapp.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.icu.text.DateFormat
import android.icu.text.ListFormatter
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import io.github.dumahuch.pogodaapp.forecast.precipitationCaption
import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.ui.home.weatherGlyphBitmap
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Уведомление о погоде (0.7.0, шаг 1): температура и погода для места, которое приложение определило последним
 * (доступа «Всегда» к месту нет, решение Димы 07.11.2026). Заголовок «Ганновер · 7° Облачно», строка «Ощущается как 5° ·
 * осадки днём». Значок в строке состояния это число температуры. На заблокированном экране без названия места, если
 * выключатель «Название места на заблокированном экране» выключен. Если место определено больше суток назад, в шапке
 * «Место с 5 ноября». Смахнуть можно: уведомление вернётся при следующем обновлении.
 */
class WeatherNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    /** Фоновое обновление: свежий прогноз для запомненного места. Без места или при ошибке уведомление остаётся прежним. */
    suspend fun refresh() {
        val prefs = settings().notifications.value
        if (!prefs.wanted) {
            cancel()
            return
        }
        val entry = cache.load() ?: return
        val client = OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}")
        val forecast = try {
            OpenMeteoParser.parse(client.fetchRaw(entry.place.latitude, entry.place.longitude))
        } catch (e: ForecastException) {
            return
        }
        post(entry.place, forecast, placeAt = entry.savedAt)
    }

    /**
     * Показать сразу по прогнозу, который сохранило приложение, если ему не больше часа. Истина, если показано;
     * иначе свежие данные принесёт фоновое обновление.
     */
    fun showFromCache(): Boolean {
        val prefs = settings().notifications.value
        if (!prefs.wanted) {
            cancel()
            return true
        }
        val entry = cache.load() ?: return false
        if (!WeatherNotice.fresh(entry.savedAt, Instant.now())) return false
        val forecast = try {
            OpenMeteoParser.parse(entry.raw)
        } catch (e: ForecastException) {
            return false
        }
        post(entry.place, forecast, placeAt = entry.savedAt)
        return true
    }

    /** Настройки читаются заново при каждом показе: выключатели могли смениться на экране настроек. */
    private fun settings() = AppSettings(PreferencesSettingsStorage(context))

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    /** Выключатели перечитываются здесь: пока шёл запрос в сеть, уведомление могли выключить. */
    private fun post(place: Place, forecast: Forecast, placeAt: Instant) {
        val prefs = settings().notifications.value
        val channel = WeatherNotice.channelFor(prefs) ?: return
        if (!manager.areNotificationsEnabled()) return
        createChannels()
        val now = Instant.now()
        val current = forecast.current
        val locale = context.resources.configuration.locales[0]
        val condition = context.getString(if (current.drizzle) R.string.wx_drizzle else conditionNameRes(current.condition))
        val headline = context.getString(R.string.notify_headline, temperatureText(current.temperature), condition)
        val title = place.name?.let { context.getString(R.string.notify_title, it, headline) } ?: headline
        val precipitation = WeatherNotice.precipitationContinuation(precipitationPhrase(forecast, now, locale), locale)
        val text = context.getString(R.string.notify_text, temperatureText(current.feelsLike), precipitation)
        val since = WeatherNotice.placeSince(placeAt, now, ZoneId.systemDefault())?.let {
            context.getString(R.string.notify_place_since, DateFormat.getInstanceForSkeleton("dMMMM", locale).format(Date.from(placeAt)))
        }
        val smallIcon = if (channel == NoticeChannel.StatusBar) {
            Icon.createWithResource(context, TemperatureIcons.of(roundTemperature(current.temperature)))
        } else {
            Icon.createWithResource(context, R.drawable.ic_notification)
        }
        val largeIcon = Icon.createWithBitmap(
            weatherGlyphBitmap(current.condition, current.isDay, settings().iconStyle.value, onLight(), largeIconPx()).asAndroidBitmap(),
        )
        fun builder(contentTitle: String) = Notification.Builder(context, channel.id)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentTitle(contentTitle)
            .setContentText(text)
            .setSubText(since)
            .setWhen(current.time.toEpochMilli())
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setLocalOnly(true)
            .setContentIntent(openApp())
        // На заблокированном экране вместо полной записи показывается эта: без названия места.
        val lockScreen = builder(headline).setVisibility(Notification.VISIBILITY_PUBLIC).build()
        val visibility = if (prefs.placeOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE
        manager.notify(NOTIFICATION_ID, builder(title).setVisibility(visibility).setPublicVersion(lockScreen).build())
    }

    /** Каналы создаются при каждом показе: так их названия следуют за языком интерфейса. */
    private fun createChannels() {
        val status = NotificationChannel(
            NoticeChannel.StatusBar.id,
            context.getString(R.string.channel_weather_status),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_weather_status_hint)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val shade = NotificationChannel(
            NoticeChannel.Shade.id,
            context.getString(R.string.channel_weather_shade),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.channel_weather_shade_hint)
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(status, shade))
    }

    private fun precipitationPhrase(forecast: Forecast, now: Instant, locale: Locale): String =
        when (val caption = precipitationCaption(forecast, now)) {
            PrecipitationCaption.None -> context.getString(R.string.precip_today_none)
            PrecipitationCaption.TomorrowMorning -> context.getString(R.string.notify_precip_tomorrow_morning)
            is PrecipitationCaption.Today -> if (caption.allDay) {
                context.getString(R.string.precip_today_all_day)
            } else {
                val names = context.resources.getStringArray(R.array.precip_day_parts)
                val list = ListFormatter.getInstance(locale).format(caption.parts.map { names[it.ordinal] })
                context.getString(R.string.precip_today_parts, list)
            }
        }

    private fun largeIconPx(): Int = (LARGE_ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceIn(64, 256)

    private fun onLight(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES

    /** Нажатие открывает приложение так же, как значок на рабочем столе (уже открытое не дублируется). */
    private fun openApp(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val NOTIFICATION_ID = 7001
        const val LARGE_ICON_DP = 48f
    }
}
