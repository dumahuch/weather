package io.github.dumahuch.pogodaapp.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.MainActivity
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Вечерняя сводка на завтра (0.8.35, решения Димы 14.11.2026): тихое уведомление в выбранное время (по умолчанию 20:00):
 * сравнение с сегодня, какая погода держится большую часть дня, температуры и «дождь да/нет» с временем. Место то же, что у
 * остальных уведомлений: последнее, которое определило приложение. Раз в сутки: день отправки запоминается. Если свежий
 * прогноз скачать не удалось, берётся сохранённый приложением, пока ему не больше шести часов.
 */
class SummaryNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    private fun settings() = AppSettings(PreferencesSettingsStorage(context))

    /** Вызывается из фоновой задачи: шлёт сводку, если пора и за сегодня её ещё не было. */
    suspend fun refresh() {
        val settings = settings()
        val prefs = settings.notifications.value
        if (!prefs.summary) {
            cancel()
            return
        }
        val device = ZonedDateTime.now()
        if (!SummaryNotice.isDue(device, prefs.summaryMinutes, settings.summarySentOn())) return
        val entry = cache.load() ?: return
        val now = Instant.now()
        val client = OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}")
        val forecast = try {
            OpenMeteoParser.parse(client.fetchRaw(entry.place.latitude, entry.place.longitude))
        } catch (e: ForecastException) {
            savedForecast(entry.raw, entry.savedAt, now) ?: return
        }
        if (post(entry.place, forecast, now, prefs.placeOnLockScreen)) settings.setSummarySentOn(device.toLocalDate())
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun savedForecast(raw: String, savedAt: Instant, now: Instant): Forecast? {
        if (Duration.between(savedAt, now) > SAVED_LIMIT) return null
        return try {
            OpenMeteoParser.parse(raw)
        } catch (e: ForecastException) {
            null
        }
    }

    /** Истина, если уведомление показано; без разрешения и без завтрашних суток в прогнозе (старый формат) ложь. */
    private fun post(place: Place, forecast: Forecast, now: Instant, placeOnLockScreen: Boolean): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val outlook = SummaryNotice.outlook(forecast, now) ?: return false
        createChannel()
        val locale = context.resources.configuration.locales[0]
        val paragraph = paragraph(outlook)
        val rain = rainLine(outlook, forecast.zone, locale)
        val plainTitle = context.getString(R.string.notify_summary_title_plain)
        val title = place.name?.let { context.getString(R.string.notify_summary_title, it) } ?: plainTitle
        val glyph = noticeGlyph(context, outlook.condition, isDay = true, windy = false)
        fun builder(contentTitle: String) = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(glyph)
            .setContentTitle(contentTitle)
            .setContentText("$paragraph $rain")
            .setStyle(Notification.BigTextStyle().bigText("$paragraph\n$rain"))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter(TIMEOUT.toMillis())
            .setContentIntent(open())
        // Без названия места на заблокированном экране, если человек так выбрал (общий выключатель с погодой в шторке).
        val lockScreen = builder(plainTitle).setVisibility(Notification.VISIBILITY_PUBLIC).build()
        val visibility = if (placeOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE
        manager.notify(NOTIFICATION_ID, builder(title).setVisibility(visibility).setPublicVersion(lockScreen).build())
        return true
    }

    /** «Завтра теплее, чем сегодня, на 4°. Пасмурно большую часть дня. Днём около 10°, ночью опустится до 3°.» */
    private fun paragraph(outlook: TomorrowOutlook): String {
        val versus = when (outlook.versus) {
            Versus.Same -> context.getString(R.string.notify_summary_same)
            Versus.Warmer -> context.getString(R.string.notify_summary_warmer, temperatureText(outlook.versusDegrees.toDouble()))
            Versus.Colder -> context.getString(R.string.notify_summary_colder, temperatureText(outlook.versusDegrees.toDouble()))
        }
        val dominant = outlook.dominant?.let {
            context.getString(R.string.notify_summary_dominant, context.getString(conditionNameRes(it)))
        } ?: context.getString(R.string.notify_summary_mixed)
        val temps = context.getString(R.string.notify_summary_temps, temperatureText(outlook.high), temperatureText(outlook.low))
        return listOf(versus, dominant, temps).joinToString(" ")
    }

    /** «Дождя не будет.» или «Дождь: да, с 14:00 до 18:00, около 3,2 мм.» */
    private fun rainLine(outlook: TomorrowOutlook, zone: ZoneId, locale: Locale): String {
        val window = outlook.rain ?: return context.getString(R.string.notify_summary_rain_none)
        val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
        val amount = "${dayPrecipitationText(window.totalMm, locale).ifEmpty { "0" }} ${context.getString(R.string.mm_unit)}"
        return context.getString(R.string.notify_summary_rain_yes, kindName(window.kind), clock.format(window.from), clock.format(window.to), amount)
    }

    private fun kindName(kind: PrecipitationKind): String = context.getString(
        when (kind) {
            PrecipitationKind.Rain -> R.string.notify_summary_kind_rain
            PrecipitationKind.Snow -> R.string.notify_summary_kind_snow
            PrecipitationKind.Storm -> R.string.notify_summary_kind_storm
            PrecipitationKind.Drizzle -> R.string.notify_summary_kind_drizzle
            PrecipitationKind.Generic -> R.string.notify_summary_kind_generic
        },
    )

    /** Канал создаётся при каждом показе: так его название следует за языком интерфейса. Тихий: без звука и вибрации. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_weather_summary),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_weather_summary_hint)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun open(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL_ID = "weather_summary"
        const val NOTIFICATION_ID = 7401
        const val REQUEST_CODE = 400
        val SAVED_LIMIT: Duration = Duration.ofHours(6)

        /** Сводка вечером лежит до утра, дальше она устарела. */
        val TIMEOUT: Duration = Duration.ofHours(14)
    }
}
