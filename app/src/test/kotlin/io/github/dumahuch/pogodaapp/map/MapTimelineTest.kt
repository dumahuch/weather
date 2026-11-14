package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Шкала времени карты: одна, с сегодняшних 00:00 до конца прогноза ICON-D2; «Сейчас» шагом 5 минут около настоящего
 * времени, прошлое по часам, прогноз ICON-D2 по 15 минут до самого конца.
 */
class MapTimelineTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** Пн 09.11.2026, 20:52 по Берлину. */
    private val now = Instant.parse("2026-11-09T19:52:30Z")

    private fun local(text: String) = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    /** Кадры суток [day]: полночь в конце уже следующие сутки, кроме конца всей шкалы. */
    private fun dayFrames(day: MapDay, now: Instant, zone: ZoneId): List<Instant> {
        val (start, end) = MapTimeline.dayRange(day, now, zone)
        return MapTimeline.frames(start, end, now, zone).filter { it.isBefore(end) || it == MapTimeline.range(now, zone).second }
    }

    @Test
    fun anchorIsLastRadarFrame() {
        assertEquals(local("2026-11-09T20:50"), MapTimeline.anchor(now))
    }

    @Test
    fun rulerRunsFromTodayMidnightToEndOfIconD2() {
        val (start, end) = MapTimeline.range(now, zone)
        assertEquals(local("2026-11-09T00:00"), start)
        assertEquals(local("2026-11-11T16:00"), end)
        // «Послезавтра» обрезано: до 16:00, а не до полуночи.
        assertEquals(local("2026-11-11T16:00"), MapTimeline.dayRange(MapDay.DayAfter, now, zone).second)
        val (todayStart, todayEnd) = MapTimeline.dayRange(MapDay.Today, now, zone)
        assertEquals(local("2026-11-09T00:00"), todayStart)
        assertEquals(local("2026-11-10T00:00"), todayEnd)
    }

    @Test
    fun dayButtonFollowsTheRuler() {
        assertEquals(MapDay.Today, MapTimeline.dayOf(local("2026-11-09T23:45"), now, zone))
        assertEquals(MapDay.Tomorrow, MapTimeline.dayOf(local("2026-11-10T00:00"), now, zone))
        assertEquals(MapDay.DayAfter, MapTimeline.dayOf(local("2026-11-11T13:00"), now, zone))
        assertEquals(MapDay.DayAfter, MapTimeline.dayOf(local("2026-11-12T00:00"), now, zone))
    }

    @Test
    fun modelEndIsLatestRunPlusTwoDays() {
        // 19:52 UTC: прогон 15 UTC уже лежит (18 UTC ещё нет), его прогноз до 15 UTC послезавтра (16:00 по Берлину).
        assertEquals(Instant.parse("2026-11-11T15:00:00Z"), MapTimeline.modelEnd(now))
    }

    @Test
    fun nearNowSnapsToFiveMinutesElsewhereToHours() {
        assertEquals(local("2026-11-09T21:10"), MapTimeline.snap(local("2026-11-09T21:11"), now, zone))
        assertEquals(local("2026-11-09T13:00"), MapTimeline.snap(local("2026-11-09T13:20"), now, zone))
        assertEquals(local("2026-11-10T07:15"), MapTimeline.snap(local("2026-11-10T07:20"), now, zone))
        // Конец прогноза ICON-D2 (16:00 послезавтра) последний кадр: дальше шкала не идёт.
        assertEquals(local("2026-11-11T15:45"), MapTimeline.snap(local("2026-11-11T15:50"), now, zone))
        assertEquals(local("2026-11-11T16:00"), MapTimeline.snap(local("2026-11-11T16:40"), now, zone))
        assertEquals(local("2026-11-11T16:00"), MapTimeline.snap(local("2026-11-12T03:00"), now, zone))
    }

    @Test
    fun afterRadarSnapsToQuarterHours() {
        // Радар до 22:50, дальше шаг ICON-D2 15 минут.
        assertEquals(local("2026-11-09T23:00"), MapTimeline.snap(local("2026-11-09T23:07"), now, zone))
        assertEquals(local("2026-11-09T23:15"), MapTimeline.snap(local("2026-11-09T23:09"), now, zone))
        assertEquals(local("2026-11-09T23:00"), MapTimeline.snap(local("2026-11-09T22:56"), now, zone))
        // Ближайшая четверть часа ещё на радаре: остаётся последний кадр радара.
        assertEquals(local("2026-11-09T22:50"), MapTimeline.snap(local("2026-11-09T22:52"), now, zone))
    }

    @Test
    fun otherDayKeepsTimeOfDayRoundedToHour() {
        assertEquals(
            local("2026-11-10T21:00"),
            MapTimeline.sameTimeOn(MapDay.Tomorrow, local("2026-11-09T20:50"), now, zone),
        )
        assertEquals(
            local("2026-11-11T16:00"),
            MapTimeline.sameTimeOn(MapDay.DayAfter, local("2026-11-10T21:00"), now, zone),
        )
        assertEquals(MapTimeline.anchor(now), MapTimeline.sameTimeOn(MapDay.Today, local("2026-11-11T05:00"), now, zone))
    }

    @Test
    fun ticksAreHourlyWithQuarterHoursWhereFramesAreFiner() {
        val ticks = MapTimeline.ticks(now, zone)
        assertEquals(65, ticks.count { it.major })
        assertEquals(local("2026-11-11T16:00"), ticks.last().time)
        val minor = ticks.filter { !it.major }.map { it.time }
        assertTrue(local("2026-11-09T19:15") in minor)
        assertTrue(local("2026-11-09T23:15") in minor)
        assertTrue(local("2026-11-10T07:45") in minor)
        assertTrue(local("2026-11-11T15:45") in minor)
        assertFalse(local("2026-11-09T12:15") in minor)
        assertFalse(local("2026-11-11T16:15") in minor)
    }

    @Test
    fun playFramesCoverTwoHoursBackAndRadarForecast() {
        val today = MapTimeline.playFrames(MapDay.Today, now, zone)
        assertEquals(local("2026-11-09T18:45"), today.first())
        assertEquals(local("2026-11-09T22:45"), today.last())
        val tomorrow = MapTimeline.playFrames(MapDay.Tomorrow, now, zone)
        assertEquals(24, tomorrow.size)
        assertEquals(local("2026-11-10T00:00"), tomorrow.first())
        // Послезавтра по часам до самого конца шкалы (16:00 включительно).
        val dayAfter = MapTimeline.playFrames(MapDay.DayAfter, now, zone)
        assertEquals(17, dayAfter.size)
        assertEquals(local("2026-11-11T16:00"), dayAfter.last())
    }

    @Test
    fun atNightDayAfterHasNoFramesYet() {
        // 00:10: последний прогон ICON-D2 доходит только до 22:00 завтра, «Послезавтра» ещё нет.
        val night = local("2026-11-09T00:10")
        assertTrue(MapTimeline.hasFrames(MapDay.Today, night, zone))
        assertTrue(MapTimeline.hasFrames(MapDay.Tomorrow, night, zone))
        assertFalse(MapTimeline.hasFrames(MapDay.DayAfter, night, zone))
        assertEquals(local("2026-11-10T22:00"), MapTimeline.range(night, zone).second)
        assertEquals(local("2026-11-10T22:00"), MapTimeline.dayRange(MapDay.Tomorrow, night, zone).second)
        assertTrue(MapTimeline.playFrames(MapDay.DayAfter, night, zone).isEmpty())
        val tomorrow = MapTimeline.playFrames(MapDay.Tomorrow, night, zone)
        assertEquals(23, tomorrow.size)
        assertEquals(local("2026-11-10T22:00"), tomorrow.last())
        assertTrue(MapTimeline.hasFrames(MapDay.DayAfter, now, zone))
    }

    @Test
    fun rainSourceAndFrames() {
        assertTrue(MapTimeline.fromRadar(local("2026-11-09T22:50"), now))
        assertFalse(MapTimeline.fromRadar(local("2026-11-09T22:55"), now))
        assertEquals(local("2026-11-10T16:00"), MapTimeline.modelRainFrame(local("2026-11-10T15:00")))
        assertEquals(local("2026-11-09T21:00"), MapTimeline.cloudHour(local("2026-11-09T20:35")))
        assertTrue(MapTimeline.isForecast(local("2026-11-09T20:55"), now))
        assertFalse(MapTimeline.isForecast(local("2026-11-09T20:50"), now))
    }

    @Test
    fun dayFramesAreExactlyThePlacesTheRulerCanStopOn() {
        for (day in MapDay.entries) {
            val frames = dayFrames(day, now, zone)
            assertEquals(frames.sorted(), frames)
            assertEquals(frames.size, frames.toSet().size)
            // Каждое из этих времён шкала оставляет как есть, а любое другое округляется к одному из них.
            assertTrue(frames.all { MapTimeline.snap(it, now, zone) == it })
            val (start, end) = MapTimeline.dayRange(day, now, zone)
            assertEquals(start, frames.first())
            assertTrue(frames.last().isBefore(end) || day == MapDay.DayAfter)
        }
        // Конец шкалы (конец прогноза ICON-D2) тоже кадр.
        assertEquals(local("2026-11-11T16:00"), dayFrames(MapDay.DayAfter, now, zone).last())
    }

    @Test
    fun warmFramesReachOverMidnight() {
        val frames = MapTimeline.warmFrames(MapDay.Today, now, zone)
        assertTrue(local("2026-11-10T01:15") in frames)
        assertFalse(local("2026-11-10T01:45") in frames)
        assertEquals(local("2026-11-09T00:00"), frames.first())
    }

    @Test
    fun todayHasFiveMinuteFramesNearNowHourlyPastAndQuarterHoursAhead() {
        val today = dayFrames(MapDay.Today, now, zone)
        assertTrue(local("2026-11-09T20:55") in today)
        assertTrue(local("2026-11-09T12:00") in today)
        assertFalse(local("2026-11-09T12:05") in today)
        assertFalse(local("2026-11-09T12:15") in today)
        assertTrue(local("2026-11-09T23:15") in today)
        assertFalse(local("2026-11-09T23:05") in today)
        val tomorrow = dayFrames(MapDay.Tomorrow, now, zone)
        assertEquals(96, tomorrow.size)
        assertTrue(local("2026-11-10T07:45") in tomorrow)
        // Послезавтра: по 15 минут с полуночи до 16:00 включительно, дальше кадров нет.
        assertEquals(64 + 1, dayFrames(MapDay.DayAfter, now, zone).size)
    }

    @Test
    fun warmUpTakesFingerZoneFirstThenFutureThenPast() {
        val frames = dayFrames(MapDay.Today, now, zone).toMutableSet()
        // Палец у «сейчас»: сначала кадры рядом, потом будущее по порядку, прошлое в конце.
        val order = generateSequence { MapTimeline.nextToWarm(frames, now, now)?.also { frames.remove(it) } }.toList()
        val anchor = MapTimeline.anchor(now)
        val firstPast = order.indexOfFirst { it.isBefore(anchor) && java.time.Duration.between(it, now).abs() > MapTimeline.FingerZone }
        val lastFuture = order.indexOfLast { !it.isBefore(anchor) && java.time.Duration.between(it, now).abs() > MapTimeline.FingerZone }
        assertTrue(lastFuture < firstPast)
        assertEquals(local("2026-11-09T20:50"), order.first())
        assertEquals(local("2026-11-09T00:00"), order.last())
    }

    @Test
    fun warmUpFollowsTheFingerIntoThePast() {
        val frames = dayFrames(MapDay.Today, now, zone).toSet()
        val finger = local("2026-11-09T09:00")
        // Кадр у пальца в прошлом берётся раньше любого кадра будущего.
        assertEquals(finger, MapTimeline.nextToWarm(frames, finger, now))
        val withoutNear = frames.filter { java.time.Duration.between(it, finger).abs() > MapTimeline.FingerZone }
        assertTrue(MapTimeline.nextToWarm(withoutNear, finger, now)!! >= MapTimeline.anchor(now))
    }

    @Test
    fun warmUpOfEmptyQueueIsNull() {
        assertEquals(null, MapTimeline.nextToWarm(emptyList(), now, now))
    }
}
