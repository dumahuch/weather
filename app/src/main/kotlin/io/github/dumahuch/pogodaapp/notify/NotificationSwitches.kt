package io.github.dumahuch.pogodaapp.notify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.NotificationToggle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Выключатели уведомлений на экране настроек. Включение шторки, строки состояния или предупреждений сначала просит
 * разрешение на уведомления (Android 13+); без него выключатель остаётся выключенным. Если человек отказал дважды,
 * системное окно больше не появляется: тогда открываются системные настройки уведомлений приложения. Создаётся вместе с
 * окном приложения.
 * Любая ошибка уведомления не роняет приложение: её текст попадает в строку под выключателями (0.7.2).
 */
class NotificationSwitches(private val activity: ComponentActivity, private val settings: AppSettings) {
    private val notifier = WeatherNotifier(activity.applicationContext)
    private val alerts = AlertNotifier(activity.applicationContext)
    private val rain = RainNotifier(activity.applicationContext)
    private val pollen = PollenNotifier(activity.applicationContext)
    private val summary = SummaryNotifier(activity.applicationContext)

    /** Что сделать, когда разрешение выдано. */
    private var pending: (() -> Unit)? = null

    private val request = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted && action != null) action()
    }

    fun set(toggle: NotificationToggle, on: Boolean) {
        if (on && toggle != NotificationToggle.LockScreenPlace) {
            withPermission { apply(toggle, on = true) }
        } else {
            apply(toggle, on)
        }
    }

    /** Новое время вечерней сводки (минуты от полуночи): запоминается, задача ставится заново. */
    fun setSummaryTime(minutes: Int) {
        settings.setSummaryMinutes(minutes)
        guarded { WeatherJobs.syncSummary(activity) }
    }

    /**
     * Первый запуск (решение Димы 14.11.2026: все уведомления включены по умолчанию): если разрешения на уведомления ещё нет
     * и о нём не спрашивали, системное окно открывается один раз. После отказа больше не беспокоим: включить можно в настройках.
     */
    fun askPermissionOnFirstRun() {
        if (!settings.notifications.value.background || allowed() || settings.notificationPermissionAsked()) return
        settings.markNotificationPermissionAsked()
        pending = { guarded { WeatherJobs.refreshSoon(activity) } }
        request.launch(PERMISSION)
    }

    /** Кнопка экрана проверки: учебное уведомление о предупреждении (со звуком, как настоящее). */
    fun showSampleAlert() {
        withPermission { guarded { alerts.showSample() } }
    }

    /** Кнопка экрана проверки: учебное «Скоро дождь». */
    fun showSampleRain() {
        withPermission { guarded { rain.showSample() } }
    }

    /** Кнопка экрана проверки: учебное уведомление о пыльце. */
    fun showSamplePollen() {
        withPermission { guarded { pollen.showSample() } }
    }

    private fun withPermission(action: () -> Unit) {
        if (allowed()) {
            action()
            return
        }
        if (settings.notificationPermissionAsked() && !activity.shouldShowRequestPermissionRationale(PERMISSION)) {
            openSystemSettings()
            return
        }
        settings.markNotificationPermissionAsked()
        pending = action
        request.launch(PERMISSION)
    }

    /** При открытии приложения: расписание фонового обновления по выключателям (после обновления версии тоже на месте). */
    fun onAppStarted() {
        guarded { WeatherJobs.sync(activity, settings.notifications.value) }
    }

    /** Приложение уходит в фон: уведомление сразу берёт прогноз, который только что получило приложение (если он свежий). */
    fun onAppHidden() {
        if (!settings.notifications.value.wanted) return
        activity.lifecycleScope.launch(Dispatchers.Default) { guarded { notifier.showFromCache() } }
    }

    private fun apply(toggle: NotificationToggle, on: Boolean) {
        settings.setNotification(toggle, on)
        if (!settings.notifications.value.background) settings.setNoticeError(null)
        guarded { WeatherJobs.sync(activity, settings.notifications.value) }
        if (toggle == NotificationToggle.Alerts) {
            // Включили: предупреждения запрашиваются сразу, как будет сеть. Выключили: прежние уведомления убираются.
            guarded { if (on) WeatherJobs.refreshSoon(activity) else alerts.cancelAll() }
            return
        }
        if (toggle == NotificationToggle.RainSoon) {
            // Включили: радар смотрится сразу, как будет сеть. Выключили: прежнее уведомление убирается.
            guarded { if (on) WeatherJobs.refreshSoon(activity) else rain.cancelAll() }
            return
        }
        if (toggle == NotificationToggle.Summary) {
            // Включили: сводка придёт в выбранное время (задача уже поставлена в sync). Выключили: уведомление убирается.
            guarded { if (!on) summary.cancel() }
            return
        }
        if (toggle == NotificationToggle.Pollen) {
            // Включили: файл DWD смотрится при ближайшем запуске задачи (в часы с 7 до 21). Выключили: уведомление убирается.
            guarded { if (on) WeatherJobs.refreshSoon(activity) else pollen.cancelAll() }
            return
        }
        activity.lifecycleScope.launch(Dispatchers.Default) {
            guarded { if (!notifier.showFromCache()) WeatherJobs.refreshSoon(activity) }
        }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settings.setNoticeError(NoticeFailure.describe(e))
        }
    }

    private fun allowed(): Boolean = activity.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        activity.startActivity(intent)
    }

    private companion object {
        const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS
    }
}
