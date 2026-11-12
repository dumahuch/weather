package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Шкала времени карты: «Сейчас» шагом 5 минут около настоящего времени, остальное по часам. */
class MapTimelineTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** Пн 09.11.2026, 20:52 по Берлину. */
    private val now = Instant.parse("2026-11-09T19:52:30Z")

    private fun local(text: String) = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun anchorIsLastRadarFrame() {
        assertEquals(local("2026-11-09T20:50"), MapTimeline.anchor(now))
    }

    @Test
    fun todayRunsFromMidnightToMidnight() {
        val (start, end) = MapTimeline.range(MapDay.Today, now, zone)
        assertEquals(local("2026-11-09T00:00"), start)
        assertEquals(local("2026-11-10T00:00"), end)
    }

    @Test
    fun lateEveningRangeReachesEndOfRadarForecast() {
        val late = local("2026-11-09T23:31")
        val (_, end) = MapTimeline.range(MapDay.Today, late, zone)
        assertEquals(local("2026-11-10T01:30"), end)
    }

    @Test
    fun nearNowSnapsToFiveMinutesElsewhereToHours() {
        assertEquals(local("2026-11-09T21:10"), MapTimeline.snap(local("2026-11-09T21:11"), MapDay.Today, now, zone))
        assertEquals(local("2026-11-09T13:00"), MapTimeline.snap(local("2026-11-09T13:20"), MapDay.Today, now, zone))
        assertEquals(local("2026-11-10T00:00"), MapTimeline.snap(local("2026-11-10T03:00"), MapDay.Today, now, zone))
        assertEquals(local("2026-11-10T07:00"), MapTimeline.snap(local("2026-11-10T07:20"), MapDay.Tomorrow, now, zone))
    }

    @Test
    fun otherDayKeepsTimeOfDayRoundedToHour() {
        assertEquals(
            local("2026-11-10T21:00"),
            MapTimeline.sameTimeOn(MapDay.Tomorrow, local("2026-11-09T20:50"), now, zone),
        )
        assertEquals(
            local("2026-11-11T21:00"),
            MapTimeline.sameTimeOn(MapDay.DayAfter, local("2026-11-10T21:00"), now, zone),
        )
        assertEquals(MapTimeline.anchor(now), MapTimeline.sameTimeOn(MapDay.Today, local("2026-11-11T05:00"), now, zone))
    }

    @Test
    fun ticksAreHourlyWithQuarterHoursNearNow() {
        val ticks = MapTimeline.ticks(MapDay.Today, now, zone)
        assertEquals(25, ticks.count { it.major })
        val minor = ticks.filter { !it.major }.map { it.time }
        assertTrue(local("2026-11-09T19:15") in minor)
        assertTrue(local("2026-11-09T22:45") in minor)
        assertFalse(local("2026-11-09T12:15") in minor)
        assertTrue(MapTimeline.ticks(MapDay.Tomorrow, now, zone).all { it.major })
    }

    @Test
    fun playFramesCoverTwoHoursBackAndRadarForecast() {
        val today = MapTimeline.playFrames(MapDay.Today, now, zone)
        assertEquals(local("2026-11-09T18:45"), today.first())
        assertEquals(local("2026-11-09T22:45"), today.last())
        val tomorrow = MapTimeline.playFrames(MapDay.Tomorrow, now, zone)
        assertEquals(24, tomorrow.size)
        assertEquals(local("2026-11-10T00:00"), tomorrow.first())
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
            val frames = MapTimeline.dayFrames(day, now, zone)
            assertEquals(frames.sorted(), frames)
            assertEquals(frames.size, frames.toSet().size)
            // Каждое из этих времён шкала оставляет как есть, а любое другое округляется к одному из них.
            assertTrue(frames.all { MapTimeline.snap(it, day, now, zone) == it })
            val (start, end) = MapTimeline.range(day, now, zone)
            assertEquals(start, frames.first())
            assertEquals(end, frames.last())
        }
    }

    @Test
    fun todayHasFiveMinuteFramesNearNowAndTomorrowOnlyHourly() {
        val today = MapTimeline.dayFrames(MapDay.Today, now, zone)
        assertTrue(local("2026-11-09T20:55") in today)
        assertTrue(local("2026-11-09T12:00") in today)
        assertFalse(local("2026-11-09T12:05") in today)
        val tomorrow = MapTimeline.dayFrames(MapDay.Tomorrow, now, zone)
        assertEquals(25, tomorrow.size)
    }

    @Test
    fun warmUpTakesFingerZoneFirstThenFutureThenPast() {
        val frames = MapTimeline.dayFrames(MapDay.Today, now, zone).toMutableSet()
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
        val frames = MapTimeline.dayFrames(MapDay.Today, now, zone).toSet()
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
