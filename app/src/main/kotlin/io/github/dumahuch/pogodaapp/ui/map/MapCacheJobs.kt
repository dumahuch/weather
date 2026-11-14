package io.github.dumahuch.pogodaapp.ui.map

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.dumahuch.pogodaapp.map.FrameDiskCache
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/** Где лежат кадры карты на диске (кэш приложения). */
fun mapCacheRoot(context: Context): File = File(context.cacheDir, "map")

/** Где лежат кадры дождя ICON-D2 по прогонам (отдельно: их не стирают ни полночь, ни задача в 23:55). */
fun mapModelRoot(context: Context): File = File(context.cacheDir, "map-model")

/**
 * Разовая задача системы, которая стирает сегодняшние кадры карты около 23:55 (см. [FrameDiskCache]). Ставится при
 * открытии карты; Android может сдвинуть запуск на несколько минут, разрешений это не требует. Если телефон в это
 * время не запускал приложение, кадры прошлых суток всё равно стираются при следующем запуске.
 */
object MapCacheJobs {
    private const val CLEANUP_ID = 7103
    private val LATE: Duration = Duration.ofMinutes(15)

    fun scheduleCleanup(context: Context) {
        val delay = FrameDiskCache.untilCleanup(ZonedDateTime.now())
        val job = JobInfo.Builder(CLEANUP_ID, ComponentName(context, MapCacheJobService::class.java))
            .setMinimumLatency(delay.toMillis())
            .setOverrideDeadline(delay.plus(LATE).toMillis())
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}

/** Стирает кадры карты на диске; запускает только система. */
class MapCacheJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val root = mapCacheRoot(applicationContext)
        thread(name = "map-cache-cleanup") {
            FrameDiskCache.purgeAll(root)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
