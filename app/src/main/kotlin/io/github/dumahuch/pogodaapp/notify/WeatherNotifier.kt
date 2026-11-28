package io.github.dumahuch.pogodaapp.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.icu.text.DateFormat
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.forecast.precipitationCaption
import io.github.dumahuch.pogodaapp.home.isStrongWind
import io.github.dumahuch.pogodaapp.home.roundTemperature
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.LockScreenForm
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.home.conditionNameRes
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.ForecastAge
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Уведомление о погоде (0.7.0, шаг 1): температура и погода для места, которое приложение определило последним
 * (доступа «Всегда» к месту нет, решение Димы 07.11.2026). Заголовок «Ганновер · 7° Облачно», строка «Ощущается как 5° ·
 * осадки днём». Значок в строке состояния это число температуры. На заблокированном экране без названия места, если
 * выключатель «Название места на заблокированном экране» выключен. Если место определено больше суток назад, в шапке
 * «Место с 5 ноября». Развёрнутый вид (0.8.35, решение Димы 14.11.2026) добавляет, что ожидается сегодня: температуры утра,
 * дня и вечера, а когда они прошли, завтрашний день. Запись постоянная и с наибольшим приоритетом. Время записи ставится
 * на момент показа (0.8.94): последним признаком порядка в шторке Android берёт именно его (свежее выше), а прежнее
 * «начало часа прогноза» опускало запись ниже более поздних. Выше уведомлений со звуком и выше записей другого
 * приложения, пришедших позже, поставить нельзя; канал «только шторка» (наименьшая важность) лежит ниже всех громких.
 */
class WeatherNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    /**
     * Фоновое обновление: свежий прогноз для запомненного места (один запрос на запуск задачи, см. [JobForecast]). Без места
     * или при ошибке уведомление остаётся прежним.
     */
    suspend fun refresh(forecastRequest: JobForecast = JobForecast(OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"))) {
        val prefs = settings().notifications.value
        if (!prefs.wanted) {
            cancel()
            return
        }
        val entry = cache.load() ?: return
        val forecast = try {
            OpenMeteoParser.parse(forecastRequest.raw(entry.place.latitude, entry.place.longitude))
        } catch (e: ForecastException) {
            // Нет сети: показываем сохранённый прогноз, пересчитанный на нынешний час, а не вчерашние градусы.
            postStored(entry)
            return
        }
        post(entry.place, forecast, placeAt = entry.savedAt)
    }

    /** Сохранённый прогноз с поправкой на возраст ([ForecastAge]); старше суток записи нет совсем. */
    private fun postStored(entry: ForecastCache.Entry) {
        val stored = try {
            OpenMeteoParser.parse(entry.raw)
        } catch (e: ForecastException) {
            null
        }
        val adjusted = stored?.let { ForecastAge.asOf(it, Instant.now()) }
        if (adjusted == null) cancel() else post(entry.place, adjusted, placeAt = entry.savedAt)
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
        val precipitation = WeatherNotice.precipitationContinuation(precipitationPhrase(context, precipitationCaption(forecast, now), locale), locale)
        val text = context.getString(R.string.notify_text, temperatureText(current.feelsLike), precipitation)
        val since = WeatherNotice.placeSince(placeAt, now, ZoneId.systemDefault())?.let {
            context.getString(R.string.notify_place_since, DateFormat.getInstanceForSkeleton("dMMMM", locale).format(Date.from(placeAt)))
        }
        val smallIcon = if (channel == NoticeChannel.StatusBar) {
            Icon.createWithResource(context, TemperatureIcons.of(roundTemperature(current.temperature)))
        } else {
            Icon.createWithResource(context, R.drawable.ic_notification)
        }
        val largeIcon = noticeGlyph(context, current.condition, current.isDay, isStrongWind(forecast.detailsAt(now)?.windKmh))
        val expanded = listOfNotNull(text, dayLine(forecast, now)).joinToString("\n")
        @Suppress("DEPRECATION") // Notification.priority ещё участвует в порядке уведомлений одного уровня (шаг 11 NotificationComparator)
        fun builder(contentTitle: String, compact: Boolean = false) = Notification.Builder(context, channel.id)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentTitle(contentTitle)
            .setContentText(if (compact) null else text)
            .setSubText(if (compact) null else since)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(!compact)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_STATUS)
            .setLocalOnly(true)
            .setContentIntent(openApp())
        // На заблокированном экране вместо полной записи показывается эта: без названия места, а при «Кратко» только
        // температура и значок (заголовок «8°», без текста и времени).
        // «Не показывать»: запись скрыта на заблокированном экране совсем и видна только после разблокировки.
        val form = prefs.lockScreen
        val full = builder(title).setStyle(Notification.BigTextStyle().bigText(expanded))
        if (form == LockScreenForm.Hidden) {
            full.setVisibility(Notification.VISIBILITY_SECRET)
        } else {
            val lockScreen = if (form == LockScreenForm.Compact) {
                builder(temperatureText(current.temperature), compact = true)
            } else {
                builder(headline)
            }.setVisibility(Notification.VISIBILITY_PUBLIC).build()
            full.setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(lockScreen)
        }
        manager.notify(NOTIFICATION_ID, full.build())
        settings().setNoticeError(null)
    }

    /** «Утро 6° · День 10° · Вечер 7°» из частей суток, что ещё впереди; когда их нет, «Завтра: днём до 10°, ночью 3°». */
    private fun dayLine(forecast: Forecast, now: Instant): String? {
        val parts = DayOutlook.remaining(forecast, now)
        if (parts.isNotEmpty()) {
            return parts.joinToString(" · ") {
                val name = when (it.stop) {
                    DayStop.Morning -> R.string.notify_day_morning
                    DayStop.Day -> R.string.notify_day_day
                    DayStop.Evening -> R.string.notify_day_evening
                }
                context.getString(name, temperatureText(it.temperature))
            }
        }
        val tomorrow = SummaryNotice.outlook(forecast, now) ?: return null
        return context.getString(R.string.notify_day_tomorrow, temperatureText(tomorrow.high), temperatureText(tomorrow.low))
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

    /** Нажатие открывает приложение так же, как значок на рабочем столе (уже открытое не дублируется). */
    private fun openApp(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val NOTIFICATION_ID = 7001
    }
}
