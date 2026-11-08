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
import io.github.dumahuch.pogodaapp.data.BrightSkyRadarClient
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.RadarParser
import io.github.dumahuch.pogodaapp.location.AssetPollenRegionLocator
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.RainSoon
import java.io.File
import java.time.Instant

/**
 * Уведомление «Скоро дождь» (0.7.0, шаг 3): та же фоновая задача, что у погоды и предупреждений, раз в полчаса смотрит радар
 * DWD над местом, которое приложение определило последним, и сообщает, если в ближайший час начнётся дождь не слабее
 * 0,5 мм/ч. Об одном дожде одно уведомление: пока в окне не станет сухо, новых нет. Только для мест в Германии.
 */
class RainNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    private fun settings() = AppSettings(PreferencesSettingsStorage(context))

    /** Фоновая проверка. Без места, без сети или при ошибке сервиса всё остаётся как было. */
    suspend fun refresh() {
        val settings = settings()
        if (!settings.notifications.value.rainSoon) {
            cancelAll()
            return
        }
        val place = cache.load()?.place ?: return
        if (AssetPollenRegionLocator(context).keyOf(place.latitude, place.longitude) == null) {
            // Радар DWD покрывает только Германию (и немного вокруг); за границей прежнее уведомление к месту не относится.
            cancelAll()
            return
        }
        val now = Instant.now()
        val client = BrightSkyRadarClient(userAgent = "PogodaApp/${AppVersion.read(context).name}")
        val frames = try {
            RadarParser.parse(client.fetchRaw(place.latitude, place.longitude, now))
        } catch (e: ForecastException) {
            null
        } ?: return
        val decision = RainSoon.decide(RainSoon.outlook(frames, now), settings.rainSoonNotified())
        if (decision.cancel) manager.cancel(NOTIFICATION_ID)
        var notified = decision.notified
        decision.notifyInMinutes?.let { minutes ->
            // Без разрешения на уведомления не помечаем: сообщим, когда оно появится, если дождь ещё впереди.
            if (manager.areNotificationsEnabled()) post(minutes) else notified = false
        }
        settings.setRainSoonNotified(notified)
    }

    fun cancelAll() {
        manager.cancel(NOTIFICATION_ID)
        settings().setRainSoonNotified(false)
    }

    private fun post(minutes: Int) {
        createChannel()
        val text = if (minutes <= RainSoon.STEP.toMinutes()) {
            context.getString(R.string.notify_rain_now)
        } else {
            context.getString(R.string.notify_rain_in, RainSoon.roundedMinutes(minutes))
        }
        fun builder() = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_rain_title))
            .setContentText(text)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter(RainSoon.WINDOW.toMillis())
            .setContentIntent(open())
        // Текст не называет места, поэтому на заблокированном экране виден целиком.
        manager.notify(NOTIFICATION_ID, builder().setVisibility(Notification.VISIBILITY_PUBLIC).build())
    }

    /** Канал создаётся при каждом показе: так его название следует за языком интерфейса. Звук системный. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_weather_rain),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_weather_rain_hint) }
        manager.createNotificationChannel(channel)
    }

    private fun open(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL_ID = "weather_rain"
        const val NOTIFICATION_ID = 7301
        const val REQUEST_CODE = 300
    }
}
