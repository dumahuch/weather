package io.github.dumahuch.pogodaapp.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.MainActivity
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.AlertsParser
import io.github.dumahuch.pogodaapp.data.BrightSkyAlertsClient
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoParser
import io.github.dumahuch.pogodaapp.location.AssetPollenRegionLocator
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.home.alertWhenLabel
import io.github.dumahuch.pogodaapp.ui.sky.SAMPLE_ALERTS
import io.github.dumahuch.pogodaapp.ui.sky.sampleAlerts
import io.github.dumahuch.pogodaapp.weather.AlertFilter
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.AlertSet
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Уведомления о предупреждениях DWD (0.7.0, шаг 2): фоновая задача раз в полчаса запрашивает Bright Sky для места, которое
 * приложение определило последним. Непогода и экстремальная непогода (уровни 3 и 4) сообщаются со звуком, по одному
 * уведомлению на явление (оно заменяется при повышении уровня и убирается, когда предупреждений этого вида больше нет или
 * кончился срок). Уровни 1 и 2 (с 25.11.2026 по выбору человека: уровень и явления, см. [AlertFilter]) собраны в одно тихое
 * уведомление. Только для мест в Германии. Какие предупреждения уже были, хранится без места и идентификаторов (см. [AlertNotices]).
 */
class AlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cache = ForecastCache(File(context.filesDir, ForecastCache.CURRENT_PLACE_FILE))

    private fun settings() = AppSettings(PreferencesSettingsStorage(context))

    /** Фоновое обновление. Без места, без сети или при ошибке сервиса уведомления остаются прежними. */
    suspend fun refresh() {
        val settings = settings()
        if (!settings.notifications.value.alerts) {
            cancelAll()
            return
        }
        val entry = cache.load() ?: return
        val place = entry.place
        if (AssetPollenRegionLocator(context).keyOf(place.latitude, place.longitude) == null) {
            // Вне Германии предупреждений DWD нет, прежние к этому месту не относятся.
            cancelAll()
            return
        }
        val client = BrightSkyAlertsClient(userAgent = "PogodaApp/${AppVersion.read(context).name}")
        val set = try {
            AlertsParser.parse(client.fetchRaw(place.latitude, place.longitude))
        } catch (e: ForecastException) {
            null
        } ?: return
        val zone = try {
            OpenMeteoParser.parse(entry.raw).zone
        } catch (e: ForecastException) {
            ZoneId.systemDefault()
        }
        show(set, zone, Instant.now())
        // Ошибку погоды здесь не стираем: её строка нужна, пока уведомление о погоде включено.
        if (!settings.notifications.value.wanted) settings.setNoticeError(null)
    }

    /** Учебное уведомление с экрана проверки: «сильная гроза» из учебных предупреждений, без записи «уже было». */
    fun showSample() {
        if (!manager.areNotificationsEnabled()) return
        createLoudChannel()
        val now = Instant.now()
        val set = sampleAlerts(SAMPLE_ALERTS, now)
        val alert = set.alerts.first { it.kind == AlertKind.Thunderstorm }
        post(alert, set.area, ZoneId.systemDefault(), now)
    }

    fun cancelAll() {
        AlertKind.entries.forEach { manager.cancel(tag(it), NOTIFICATION_ID) }
        manager.cancel(QUIET_TAG, NOTIFICATION_ID)
    }

    private fun show(set: AlertSet, zone: ZoneId, now: Instant) {
        val settings = settings()
        val shown = set.shownAt(now)
        val notified = AlertNotices.decode(settings.notifiedAlerts())
        val plan = AlertNotices.plan(shown, notified, settings.alertFilter.value)
        plan.cancelLoud.forEach { manager.cancel(tag(it), NOTIFICATION_ID) }
        if (plan.cancelQuiet) manager.cancel(QUIET_TAG, NOTIFICATION_ID)
        // Без разрешения на уведомления о новых предупреждениях не помечаем: о них сообщим, когда разрешение появится.
        val posted = mutableListOf<WeatherAlert>()
        if (manager.areNotificationsEnabled()) {
            if (plan.loud.isNotEmpty()) {
                createLoudChannel()
                plan.loud.forEach { post(it, set.area, zone, now) }
                posted += plan.loud
            }
            if (plan.quietFresh) {
                createQuietChannel()
                postQuiet(plan.quiet, set.area, zone, now)
                posted += plan.quiet
            }
        }
        settings.setNotifiedAlerts(AlertNotices.encode(AlertNotices.remember(notified, shown, posted, now)))
    }

    /** Заголовок: явление и когда оно (или только явление, если время неизвестно). */
    private fun titleOf(alert: WeatherAlert, zone: ZoneId, now: Instant): String {
        val kind = context.resources.getStringArray(R.array.alert_kinds)[alert.kind.ordinal]
        return alertWhenLabel(alert, now, zone, context.resources.getStringArray(R.array.days_short))
            ?.let { context.getString(R.string.notify_alert_title, kind, context.getString(it.res, *it.args)) }
            ?: kind
    }

    private fun levelLine(alert: WeatherAlert): String {
        val levelName = context.resources.getStringArray(R.array.alert_levels)[alert.level.number - 1]
        return context.getString(R.string.alert_level_line, alert.level.number, levelName)
    }

    private fun post(alert: WeatherAlert, area: String?, zone: ZoneId, now: Instant) {
        val title = titleOf(alert, zone, now)
        val level = levelLine(alert)
        val headline = headlineOf(alert)
        fun builder() = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PogodaColors.AlertLevels[alert.level.number - 1].toArgb())
            .setContentTitle(title)
            .setContentText(level)
            .setWhen((alert.issued ?: now).toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setContentIntent(open(alert))
            .also { builder -> alert.expires?.let { builder.setTimeoutAfter(Duration.between(now, it).toMillis().coerceAtLeast(1)) } }
        // На заблокированном экране без района: он выдаёт место (как и у уведомления о погоде).
        val lockScreen = builder().setVisibility(Notification.VISIBILITY_PUBLIC).build()
        val full = builder()
            .setSubText(area)
            .setStyle(Notification.BigTextStyle().bigText("$level\n$headline"))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .build()
        manager.notify(tag(alert.kind), NOTIFICATION_ID, full)
    }

    /**
     * Одно тихое уведомление обо всех нынешних предупреждениях уровней 1 и 2 ([alerts] от самого серьёзного): заголовок у
     * первого, остальные строками в развёрнутом виде. Живёт, пока не кончится срок у последнего из них.
     */
    private fun postQuiet(alerts: List<WeatherAlert>, area: String?, zone: ZoneId, now: Instant) {
        val first = alerts.first()
        val firstTitle = titleOf(first, zone, now)
        val title = if (alerts.size > 1) context.getString(R.string.notify_alert_title_more, firstTitle, alerts.size - 1) else firstTitle
        val levelNames = context.resources.getStringArray(R.array.alert_levels)
        val lines = alerts.joinToString("\n") {
            context.getString(R.string.notify_title, titleOf(it, zone, now), levelNames[it.level.number - 1])
        }
        val level = levelLine(first)
        val expires = alerts.mapNotNull { it.expires }.takeIf { it.size == alerts.size }?.max()
        fun builder() = Notification.Builder(context, QUIET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PogodaColors.AlertLevels[first.level.number - 1].toArgb())
            .setContentTitle(title)
            .setContentText(if (alerts.size > 1) alerts.drop(1).joinToString(", ") { titleOf(it, zone, now) } else level)
            .setWhen((alerts.maxOf { it.issued ?: now }).toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(first, REQUEST_QUIET))
            .also { builder -> expires?.let { builder.setTimeoutAfter(Duration.between(now, it).toMillis().coerceAtLeast(1)) } }
        val lockScreen = builder().setVisibility(Notification.VISIBILITY_PUBLIC).build()
        val full = builder()
            .setSubText(area)
            .setStyle(Notification.BigTextStyle().bigText(if (alerts.size > 1) lines else "$level\n${headlineOf(first)}"))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .build()
        manager.notify(QUIET_TAG, NOTIFICATION_ID, full)
    }

    private fun headlineOf(alert: WeatherAlert): String =
        if (context.resources.configuration.locales[0].language == "en") alert.headlineEn else alert.headlineDe

    /** Канал громких предупреждений создаётся при каждом показе: так его название следует за языком интерфейса. Звук и вибрация системные. */
    private fun createLoudChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_weather_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_weather_alerts_hint)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** Тихий канал для уровней 1 и 2: без звука и вибрации (важность канала после создания не меняется, поэтому он новый). */
    private fun createQuietChannel() {
        val channel = NotificationChannel(
            QUIET_CHANNEL_ID,
            context.getString(R.string.channel_weather_alerts_quiet),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_weather_alerts_quiet_hint)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Нажатие открывает приложение (уже открытое не дублируется) и в нём это предупреждение. */
    private fun open(alert: WeatherAlert, request: Int = REQUEST_BASE + alert.kind.ordinal): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ALERT_ID, alert.id)
            .putExtra(EXTRA_ALERT_KIND, alert.kind.name)
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun tag(kind: AlertKind) = "alert-${kind.name}"

    companion object {
        const val CHANNEL_ID = "weather_alerts"
        private const val QUIET_CHANNEL_ID = "weather_alerts_quiet"
        private const val QUIET_TAG = "alerts-quiet"
        private const val REQUEST_QUIET = 150
        private const val NOTIFICATION_ID = 7201
        private const val REQUEST_BASE = 100
        private const val EXTRA_ALERT_ID = "io.github.dumahuch.pogodaapp.ALERT_ID"
        private const val EXTRA_ALERT_KIND = "io.github.dumahuch.pogodaapp.ALERT_KIND"

        /** Предупреждение из нажатого уведомления; `null`, если приложение открыто не из него. */
        fun requestOf(intent: Intent?): AlertRequest? {
            val id = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return null
            val kind = AlertKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_ALERT_KIND) } ?: AlertKind.Other
            return AlertRequest(id, kind)
        }

        /** Учебные предупреждения (с экрана проверки) открываются вместе с ним. */
        fun isSample(request: AlertRequest): Boolean = request.id.startsWith("sample-")
    }
}
