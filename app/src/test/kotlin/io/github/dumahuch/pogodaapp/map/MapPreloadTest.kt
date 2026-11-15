package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Утренняя подгрузка карты: когда запускается, что подгружается и как переживает сбои. */
class MapPreloadTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-10T19:52:00Z")

    @Test
    fun startsAtSixTodayIfNotYetPassedOtherwiseTomorrow() {
        assertEquals(Duration.ofHours(1), MapPreload.delayUntilNext(ZonedDateTime.of(2026, 11, 15, 5, 0, 0, 0, zone)))
        assertEquals(Duration.ofHours(24), MapPreload.delayUntilNext(ZonedDateTime.of(2026, 11, 15, 6, 0, 0, 0, zone)))
        assertEquals(Duration.ofHours(8), MapPreload.delayUntilNext(ZonedDateTime.of(2026, 11, 15, 22, 0, 0, 0, zone)))
    }

    @Test
    fun hoursOfThreeLayersAndModelRainAfterRadar() {
        val tasks = MapPreload.tasks(now)
        fun of(kind: PreloadKind) = tasks.filter { it.kind == kind }.map { it.time }
        // Часы от 19:00 на сутки вперёд (25 штук), все целые.
        assertEquals(25, of(PreloadKind.Clouds).size)
        assertEquals(of(PreloadKind.Clouds), of(PreloadKind.Wind))
        assertEquals(of(PreloadKind.Clouds), of(PreloadKind.Temperature))
        assertEquals(Instant.parse("2026-11-10T19:00:00Z"), of(PreloadKind.Clouds).first())
        assertTrue(of(PreloadKind.Clouds).all { it.epochSecond % 3600 == 0L })
        // Дождь модели только после радара, шаг 15 минут.
        val rain = of(PreloadKind.Rain)
        assertTrue(rain.none { MapTimeline.fromRadar(it, now) })
        assertEquals(Duration.ofMinutes(15), Duration.between(rain[0], rain[1]))
        assertEquals(Instant.parse("2026-11-11T19:45:00Z"), rain.last())
        // Порядок по времени.
        assertEquals(tasks.map { it.time }.sorted(), tasks.map { it.time })
    }

    @Test
    fun everyTaskIsRunOnce() {
        val seen = ConcurrentHashMap<PreloadTask, Int>()
        val tasks = MapPreload.tasks(now)
        val result = MapPreload.run(tasks, stop = { false }) { seen.merge(it, 1, Int::plus) }
        assertEquals(tasks.size, result.done)
        assertEquals(0, result.failed)
        assertTrue(seen.values.all { it == 1 })
        assertEquals(tasks.toSet(), seen.keys)
    }

    @Test
    fun failedFrameDoesNotStopTheRestButThreeInARowStopTheKind() {
        val tasks = MapPreload.tasks(now)
        val windDone = AtomicInteger()
        val result = MapPreload.run(tasks, stop = { false }) { task ->
            when (task.kind) {
                // Облака: первый кадр не удался, остальные да.
                PreloadKind.Clouds -> if (task.time == tasks.first { it.kind == PreloadKind.Clouds }.time) error("нет файла")
                // Дождь: всё не удаётся, поток останавливается после трёх подряд.
                PreloadKind.Rain -> error("нет сети")
                else -> windDone.incrementAndGet()
            }
        }
        assertEquals(1 + 3, result.failed)
        assertEquals(25 - 1 + windDone.get(), result.done)
        assertTrue(windDone.get() >= 50)
    }

    @Test
    fun stopRequestEndsWorkBetweenFrames() {
        val count = AtomicInteger()
        val tasks = MapPreload.tasks(now)
        val result = MapPreload.run(tasks, stop = { count.get() >= 5 }) { count.incrementAndGet() }
        assertTrue(result.done < tasks.size)
        assertFalse(result.done == 0)
    }
}
