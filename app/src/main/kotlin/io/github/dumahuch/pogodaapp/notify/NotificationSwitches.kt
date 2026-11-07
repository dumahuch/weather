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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Выключатели уведомления о погоде на экране настроек. Включение шторки или строки состояния сначала просит разрешение на
 * уведомления (Android 13+); без него выключатель остаётся выключенным. Если человек отказал дважды, системное окно больше
 * не появляется: тогда открываются системные настройки уведомлений приложения. Создаётся вместе с окном приложения.
 */
class NotificationSwitches(private val activity: ComponentActivity, private val settings: AppSettings) {
    private val notifier = WeatherNotifier(activity.applicationContext)
    private var pending: NotificationToggle? = null

    private val request = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val toggle = pending
        pending = null
        if (granted && toggle != null) apply(toggle, on = true)
    }

    fun set(toggle: NotificationToggle, on: Boolean) {
        if (on && toggle != NotificationToggle.LockScreenPlace && !allowed()) {
            if (settings.notificationPermissionAsked() && !activity.shouldShowRequestPermissionRationale(PERMISSION)) {
                openSystemSettings()
                return
            }
            settings.markNotificationPermissionAsked()
            pending = toggle
            request.launch(PERMISSION)
            return
        }
        apply(toggle, on)
    }

    /** При открытии приложения: расписание фонового обновления по выключателям (после обновления версии тоже на месте). */
    fun onAppStarted() {
        WeatherJobs.sync(activity, settings.notifications.value)
    }

    /** Приложение уходит в фон: уведомление сразу берёт прогноз, который только что получило приложение (если он свежий). */
    fun onAppHidden() {
        if (!settings.notifications.value.wanted) return
        activity.lifecycleScope.launch(Dispatchers.Default) { notifier.showFromCache() }
    }

    private fun apply(toggle: NotificationToggle, on: Boolean) {
        settings.setNotification(toggle, on)
        WeatherJobs.sync(activity, settings.notifications.value)
        activity.lifecycleScope.launch(Dispatchers.Default) {
            if (!notifier.showFromCache()) WeatherJobs.refreshSoon(activity)
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
