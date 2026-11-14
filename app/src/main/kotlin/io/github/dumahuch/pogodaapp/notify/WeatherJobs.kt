package io.github.dumahuch.pogodaapp.notify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.github.dumahuch.pogodaapp.settings.AppSettings
import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.ui.sky.SkyBackgroundSnapshot
import io.github.dumahuch.pogodaapp.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Фоновое обновление виджета и уведомления о погоде, предупреждений DWD, «скоро дождь» и пыльцы через JobScheduler системы (без сторонних библиотек). Раз в
 * полчаса, только при сети; Android сам сдвигает запуск ради батареи, ночью реже. Задача переживает перезагрузку телефона.
 */
object WeatherJobs {
    private const val PERIODIC_ID = 7101
    private const val NOW_ID = 7102
    private val PERIOD: Duration = Duration.ofMinutes(30)
    private val FLEX: Duration = Duration.ofMinutes(10)

    /**
     * Включает или снимает периодическое обновление по выключателям и по виджетам (пока на рабочем столе есть виджет
     * погоды, обновление нужно ему); повторный вызов ничего не дублирует.
     */
    fun sync(context: Context, prefs: NotificationPrefs) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!prefs.background && !WidgetUpdater(context).hasWidgets()) {
            scheduler.cancel(PERIODIC_ID)
            scheduler.cancel(NOW_ID)
            return
        }
        if (scheduler.getPendingJob(PERIODIC_ID) != null) return
        val job = JobInfo.Builder(PERIODIC_ID, ComponentName(context, WeatherJobService::class.java))
            .setPeriodic(PERIOD.toMillis(), FLEX.toMillis())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    /** Одно обновление как можно скорее (как только будет сеть): когда сохранённого свежего прогноза нет. */
    fun refreshSoon(context: Context) {
        val job = JobInfo.Builder(NOW_ID, ComponentName(context, WeatherJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}

/**
 * Обновляет виджет, уведомление о погоде, предупреждения, «скоро дождь» и пыльцу; запросы в сеть идут в фоне, система ждёт их до [jobFinished]. Сбой одного
 * не мешает другому.
 */
class WeatherJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                guarded("виджет не обновлён") { WidgetUpdater(applicationContext).refresh() }
                guarded("уведомление о погоде не обновлено") { WeatherNotifier(applicationContext).refresh() }
                guarded("снимок неба не записан") { SkyBackgroundSnapshot(applicationContext).refresh() }
                guarded("предупреждения не обновлены") { AlertNotifier(applicationContext).refresh() }
                guarded("«скоро дождь» не проверено") { RainNotifier(applicationContext).refresh() }
                guarded("пыльца не проверена") { PollenNotifier(applicationContext).refresh() }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /**
     * Сбой в фоне не должен ронять приложение: уведомление останется прежним до следующего запуска, текст ошибки виден в
     * настройках.
     */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, what, e)
            AppSettings(PreferencesSettingsStorage(applicationContext)).setNoticeError(NoticeFailure.describe(e))
        }
    }

    /** Система прервала задачу (пропала сеть, экономия батареи): запрос отменяется, следующий запуск по расписанию. */
    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PogodaNotify"
    }
}
