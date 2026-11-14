package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Что подгружается заранее: дождь модели (кадры по 15 минут после радара), облака, температура, ветер. */
enum class PreloadKind { Rain, Clouds, Temperature, Wind }

/** Один кадр подгрузки: вид и время (для облаков, температуры и ветра целый час). */
data class PreloadTask(val kind: PreloadKind, val time: Instant)

/** Итог подгрузки: сколько кадров обработано и сколько не удалось. */
data class PreloadResult(val done: Int, val failed: Int)

/**
 * Утренняя подгрузка карты (0.8.40): раз в сутки около [AT], только по Wi-Fi, все слои модели на [HORIZON] вперёд
 * ложатся на диск ([ModelFrames]), чтобы карта открывалась без ожидания. Радар заранее не качается (его будущего нет, а
 * последние два часа и так грузятся быстро). Что уже лежит на диске от нынешнего прогона, не скачивается второй раз.
 */
object MapPreload {
    /** Во сколько запускается подгрузка (местное время); система может сдвинуть запуск на потом. */
    val AT: LocalTime = LocalTime.of(6, 0)

    /** На сколько вперёд подгружается. */
    val HORIZON: Duration = Duration.ofHours(24)

    /** После стольких неудач подряд работа по этому виду останавливается (пропала сеть, DWD не отвечает). */
    private const val MAX_FAILURES = 3

    private val Hour: Duration = Duration.ofHours(1)

    /** Сколько ждать до ближайших [AT]: сегодня, если ещё не наступили, иначе завтра. */
    fun delayUntilNext(now: ZonedDateTime): Duration {
        var at = now.toLocalDate().atTime(AT).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Duration.between(now, at)
    }

    /**
     * Кадры для подгрузки по порядку времени: каждый час от нынешнего на [HORIZON] вперёд (облака, температура, ветер) и
     * кадры дождя модели по 15 минут после радара ([MapTimeline.fromRadar]).
     */
    fun tasks(now: Instant): List<PreloadTask> {
        val end = minOf(now.plus(HORIZON), MapTimeline.modelEnd(now))
        val tasks = mutableListOf<PreloadTask>()
        var hour = MapTimeline.floorTo(now, Hour)
        while (!hour.isAfter(end)) {
            tasks += PreloadTask(PreloadKind.Clouds, hour)
            tasks += PreloadTask(PreloadKind.Temperature, hour)
            tasks += PreloadTask(PreloadKind.Wind, hour)
            hour = hour.plus(Hour)
        }
        var time = MapTimeline.floorTo(now, IconD2Rain.Step)
        while (!time.isAfter(end)) {
            if (!MapTimeline.fromRadar(time, now)) tasks += PreloadTask(PreloadKind.Rain, time)
            time = time.plus(IconD2Rain.Step)
        }
        return tasks.sortedBy { it.time }
    }

    /**
     * Выполняет [tasks] ([load] на каждый кадр) тремя потоками: дождь по порядку времени одним (кадры часа делят скачанный
     * файл), облака другим, температура и ветер третьим. [stop] остановит работу между кадрами (система прервала
     * задачу). Сбой кадра не останавливает остальные; [MAX_FAILURES] сбоев подряд останавливают поток вида.
     */
    fun run(tasks: List<PreloadTask>, stop: () -> Boolean, log: (String) -> Unit = {}, load: (PreloadTask) -> Unit): PreloadResult {
        val done = AtomicInteger()
        val failed = AtomicInteger()
        val groups = tasks.groupBy { if (it.kind == PreloadKind.Temperature || it.kind == PreloadKind.Wind) "fields" else it.kind.name }
        val workers = groups.map { (name, list) ->
            thread(name = "map-preload-$name") {
                var streak = 0
                try {
                    for (task in list) {
                        if (stop() || streak >= MAX_FAILURES) break
                        try {
                            load(task)
                            done.incrementAndGet()
                            streak = 0
                        } catch (e: Exception) {
                            failed.incrementAndGet()
                            streak++
                            log("${task.kind} ${task.time}: ${e.message}")
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    log("$name: не хватило памяти")
                }
            }
        }
        workers.forEach { it.join() }
        return PreloadResult(done.get(), failed.get())
    }
}
