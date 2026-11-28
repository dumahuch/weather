package io.github.dumahuch.pogodaapp.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.github.dumahuch.pogodaapp.JobIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.time.Duration

/** Проверка обновления раз в сутки при любой сети (один короткий запрос к GitHub), пока включён выключатель. */
object UpdateJobs {
    private const val ID = JobIds.UPDATE_CHECK
    private val PERIOD: Duration = Duration.ofHours(24)
    private val FLEX: Duration = Duration.ofHours(6)

    /** Ставит или снимает задачу по выключателю; повторный вызов ничего не дублирует. */
    fun sync(context: Context, auto: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!auto) {
            scheduler.cancel(ID)
            return
        }
        if (scheduler.getPendingJob(ID) != null) return
        val job = JobInfo.Builder(ID, ComponentName(context, UpdateJobService::class.java))
            .setPeriodic(PERIOD.toMillis(), FLEX.toMillis())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}

/** Проверяет выпуск на GitHub и при новой версии присылает уведомление (один раз на версию). Сбой: до следующих суток. */
class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                UpdateCenter.get(applicationContext).checkInBackground()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PogodaUpdate", "проверка обновления не удалась", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
