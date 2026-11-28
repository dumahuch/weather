package io.github.dumahuch.pogodaapp.ui.map

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.JobIds
import io.github.dumahuch.pogodaapp.map.MapPreload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Утренняя подгрузка карты (0.8.40): раз в сутки около 6 утра система запускает [MapPreloadJobService], и все слои
 * модели на сутки вперёд ложатся на диск ([MapPreload]). Только по Wi-Fi и не при севшей батарее: около 150 МБ за раз,
 * выбор «Подгрузка карты» на неё не действует (по мобильной сети фоном карта не качает вообще). Крайнего срока у задачи
 * нет нарочно: срок систему обязывает запустить задачу и без Wi-Fi.
 */
object MapPreloadJobs {
    /** Задача, которая идёт сейчас, ставит следующую под другим номером: одинаковый номер остановил бы её саму. */
    private const val ID_A = JobIds.MAP_PRELOAD_A
    private const val ID_B = JobIds.MAP_PRELOAD_B

    /**
     * Ставит задачу на ближайшие [MapPreload.AT], если она ещё не стоит; повторный вызов ничего не дублирует. [runningId]
     * номер задачи, которая идёт сейчас (вызов из неё): следующая ставится под другим номером.
     */
    fun sync(context: Context, runningId: Int? = null) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (runningId == null && (scheduler.getPendingJob(ID_A) != null || scheduler.getPendingJob(ID_B) != null)) return
        val next = if (runningId == ID_A) ID_B else ID_A
        val delay = MapPreload.delayUntilNext(ZonedDateTime.now())
        val job = JobInfo.Builder(next, ComponentName(context, MapPreloadJobService::class.java))
            .setMinimumLatency(delay.toMillis())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}

/** Кладёт кадры модели на диск заранее; запускает только система. Сбой не роняет приложение: кадры подгрузятся при открытии карты. */
class MapPreloadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        scope.launch {
            try {
                preload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Подгрузка карты не удалась", e)
            } finally {
                try {
                    MapPreloadJobs.sync(applicationContext, params.jobId)
                } catch (e: Exception) {
                    Log.w(TAG, "Подгрузка карты не поставлена", e)
                }
                jobFinished(params, false)
            }
        }
        return true
    }

    private fun preload() {
        val context = applicationContext
        val frames = modelFrames("PogodaApp/${AppVersion.read(context).name}", mapModelRoot(context)) { Log.w(TAG, it) }
        val now = Instant.now()
        val result = MapPreload.run(MapPreload.tasks(now), stop = { stopped }, log = { Log.w(TAG, it) }) { task ->
            frames.preload(task, now)
        }
        Log.w(TAG, "Подгрузка карты: ${result.done} кадров, не удалось ${result.failed}")
    }

    /** Система прервала задачу (пропал Wi-Fi, экономия батареи): кадры, что уже легли на диск, остаются, остальное позже. */
    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PogodaMapPreload"
    }
}
