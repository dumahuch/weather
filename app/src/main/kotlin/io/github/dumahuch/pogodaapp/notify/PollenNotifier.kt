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
import io.github.dumahuch.pogodaapp.data.DwdPollenClient
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.data.PollenParser
import io.github.dumahuch.pogodaapp.location.AssetPollenRegionLocator
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import io.github.dumahuch.pogodaapp.weather.PollenNotice
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Уведомление о пыльце (0.7.0, шаг 4): та же фоновая задача, что у погоды, предупреждений и «скоро дождь». Не чаще чем раз в
 * три часа и только с 7 до 21 часа смотрит индекс DWD для района, где последнее место приложения; если у выбранных видов на
 * сегодня нагрузка не ниже «от средней до высокой», шлёт одно уведомление за день. Только Германия.
 */
class PollenNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    private fun settings() = AppSettings(PreferencesSettingsStorage(context))

    /** Фоновая проверка. Без места, без сети или при ошибке сервиса всё остаётся как было. */
    suspend fun refresh() {
        val settings = settings()
        if (!settings.notifications.value.pollen) {
            cancelAll()
            return
        }
        val entry = cache.load() ?: return
        val region = AssetPollenRegionLocator(context).keyOf(entry.place.latitude, entry.place.longitude)
        if (region == null) {
            // Индекс DWD только для Германии; за границей прежнее уведомление к месту не относится.
            cancelAll()
            return
        }
        val zone = try {
            OpenMeteoParser.parse(entry.raw).zone
        } catch (e: ForecastException) {
            ZoneId.systemDefault()
        }
        val now = Instant.now()
        if (!PollenNotice.due(now, zone, settings.pollenCheckedAt(), settings.pollenNotifiedOn())) return
        val raw = try {
            DwdPollenClient(userAgent = "PogodaApp/${AppVersion.read(context).name}").fetchRaw()
        } catch (e: ForecastException) {
            return
        }
        settings.setPollenCheckedAt(now)
        val today = now.atZone(zone).toLocalDate()
        val forecast = PollenParser.parse(raw, region, today) ?: return
        val hits = PollenNotice.hits(forecast, today, settings.pollenKinds.value)
        // Без разрешения на уведомления день не помечаем: сообщим при следующей проверке, когда оно появится.
        if (hits.isEmpty() || !manager.areNotificationsEnabled()) return
        post(hits)
        settings.setPollenNotifiedOn(today)
    }

    fun cancelAll() {
        manager.cancel(NOTIFICATION_ID)
    }

    /** Учебное уведомление с экрана проверки: берёза «высокая», злаки «средняя», без записи «уже было». */
    fun showSample() {
        if (!manager.areNotificationsEnabled()) return
        post(listOf(PollenKind.Birch to PollenLevel(6), PollenKind.Grass to PollenLevel(4)))
    }

    private fun post(hits: List<Pair<PollenKind, PollenLevel>>) {
        createChannel()
        val locale = context.resources.configuration.locales[0]
        val kinds = context.resources.getStringArray(R.array.pollen_kinds)
        val levels = context.resources.getStringArray(R.array.pollen_levels)
        val text = hits.joinToString("\n") { (kind, level) -> "${kinds[kind.ordinal]}: ${levels[level.halves].lowercase(locale)}" }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_pollen_title))
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter(TIMEOUT.toMillis())
            .setContentIntent(open())
        // Текст не называет места, поэтому на заблокированном экране виден целиком.
        manager.notify(NOTIFICATION_ID, builder.setVisibility(Notification.VISIBILITY_PUBLIC).build())
    }

    /** Канал создаётся при каждом показе: так его название следует за языком интерфейса. Звук системный. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_weather_pollen),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_weather_pollen_hint) }
        manager.createNotificationChannel(channel)
    }

    private fun open(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL_ID = "weather_pollen"
        const val NOTIFICATION_ID = 7401
        const val REQUEST_CODE = 400
        val TIMEOUT: Duration = Duration.ofHours(12)
    }
}
