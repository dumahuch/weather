package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.AlertFilter
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertNoticesTest {
    private val now = Instant.parse("2026-11-07T15:00:00Z")

    private fun at(hours: Long): Instant = now.plus(Duration.ofHours(hours))

    private fun alert(id: String, kind: AlertKind, level: AlertLevel, from: Long, to: Long?) = WeatherAlert(
        id = id,
        level = level,
        kind = kind,
        onset = at(from),
        expires = to?.let(::at),
        issued = now,
        headlineDe = "",
        headlineEn = "",
        descriptionDe = "",
        descriptionEn = "",
        instructionDe = null,
        instructionEn = null,
    )

    private fun record(alert: WeatherAlert) = NotifiedAlert(alert.kind, alert.level.number, alert.onset, alert.expires)

    private val everything = AlertFilter(minLevel = 1, kinds = AlertKind.entries.toSet())

    private fun loud(shown: List<WeatherAlert>, notified: List<NotifiedAlert> = emptyList(), filter: AlertFilter = everything) =
        AlertNotices.plan(shown, notified, filter).loud

    @Test
    fun loudAlertsAreNotifiedOncePerKindStartingWithTheMostSevere() {
        val storm = alert("a", AlertKind.Thunderstorm, AlertLevel.Severe, 0, 4)
        val stormLater = alert("b", AlertKind.Thunderstorm, AlertLevel.Extreme, 10, 12)
        val wind = alert("c", AlertKind.Wind, AlertLevel.Extreme, 8, 18)
        assertEquals(listOf(storm, wind), loud(listOf(storm, wind, stormLater)))
    }

    @Test
    fun mildAlertsGoToOneQuietNotificationOnePerKind() {
        val wind = alert("a", AlertKind.Wind, AlertLevel.Moderate, 0, 6)
        val windLater = alert("b", AlertKind.Wind, AlertLevel.Minor, 10, 12)
        val frost = alert("c", AlertKind.Frost, AlertLevel.Minor, 8, 18)
        val plan = AlertNotices.plan(listOf(wind, frost, windLater), emptyList(), everything)
        assertEquals(emptyList<WeatherAlert>(), plan.loud)
        assertEquals(listOf(wind, frost), plan.quiet)
        assertTrue(plan.quietFresh)
        assertFalse(plan.cancelQuiet)
    }

    @Test
    fun filterDropsLowLevelsAndUnchosenKindsButNeverSevereOnes() {
        val filter = AlertFilter()
        val frostSevere = alert("a", AlertKind.Frost, AlertLevel.Severe, 0, 6)
        val frostMild = alert("b", AlertKind.Frost, AlertLevel.Moderate, 0, 6)
        val windMinor = alert("c", AlertKind.Wind, AlertLevel.Minor, 0, 6)
        val windModerate = alert("d", AlertKind.Wind, AlertLevel.Moderate, 0, 6)
        val plan = AlertNotices.plan(listOf(frostSevere, frostMild, windMinor, windModerate), emptyList(), filter)
        assertEquals(listOf(frostSevere), plan.loud)
        assertEquals(listOf(windModerate), plan.quiet)
    }

    @Test
    fun quietNotificationIsUpdatedOnlyWhenSomethingNewAppearsAndGoesWhenNothingIsLeft() {
        val wind = alert("a", AlertKind.Wind, AlertLevel.Moderate, 0, 6)
        val rain = alert("b", AlertKind.Rain, AlertLevel.Moderate, 2, 8)
        val seen = AlertNotices.plan(listOf(wind), listOf(record(wind)), everything)
        assertFalse(seen.quietFresh)
        assertFalse(seen.cancelQuiet)
        val added = AlertNotices.plan(listOf(wind, rain), listOf(record(wind)), everything)
        assertTrue(added.quietFresh)
        assertEquals(listOf(wind, rain), added.quiet)
        val none = AlertNotices.plan(emptyList(), listOf(record(wind)), everything)
        assertTrue(none.cancelQuiet)
        assertEquals(AlertKind.entries.toSet(), none.cancelLoud)
    }

    @Test
    fun raisedLevelMovesFromQuietToLoud() {
        val moderate = alert("a", AlertKind.Wind, AlertLevel.Moderate, 0, 6)
        val severe = alert("b", AlertKind.Wind, AlertLevel.Severe, 1, 6)
        val plan = AlertNotices.plan(listOf(severe), listOf(record(moderate)), everything)
        assertEquals(listOf(severe), plan.loud)
        assertTrue(plan.cancelQuiet)
        assertTrue(AlertKind.Wind !in plan.cancelLoud)
    }

    @Test
    fun updatedAlertWithNewIdDoesNotComeAgain() {
        val first = alert("a", AlertKind.Wind, AlertLevel.Severe, 0, 6)
        // DWD продлил то же предупреждение: новый идентификатор, тот же уровень, период пересекается.
        val extended = alert("a2", AlertKind.Wind, AlertLevel.Severe, 0, 9)
        assertEquals(emptyList<WeatherAlert>(), loud(listOf(extended), listOf(record(first))))
    }

    @Test
    fun higherLevelComesAgainLowerDoesNot() {
        val severe = alert("a", AlertKind.Wind, AlertLevel.Severe, 0, 6)
        val extreme = alert("b", AlertKind.Wind, AlertLevel.Extreme, 1, 6)
        val lower = alert("c", AlertKind.Wind, AlertLevel.Moderate, 1, 6)
        assertEquals(listOf(extreme), loud(listOf(extreme), listOf(record(severe))))
        val plan = AlertNotices.plan(listOf(lower), listOf(record(severe)), everything)
        assertEquals(emptyList<WeatherAlert>(), plan.loud)
        assertFalse(plan.quietFresh)
    }

    @Test
    fun sameKindInAnotherPeriodOrAnotherKindComesAgain() {
        val today = alert("a", AlertKind.Frost, AlertLevel.Severe, 2, 10)
        val tomorrow = alert("b", AlertKind.Frost, AlertLevel.Severe, 26, 34)
        val fog = alert("c", AlertKind.Fog, AlertLevel.Severe, 2, 10)
        assertEquals(listOf(tomorrow, fog), loud(listOf(tomorrow, fog), listOf(record(today))))
    }

    @Test
    fun touchingPeriodsCountAsTheSameAlertAndOpenEndCoversEverythingLater() {
        val first = alert("a", AlertKind.Rain, AlertLevel.Severe, 0, 6)
        val next = alert("b", AlertKind.Rain, AlertLevel.Severe, 6, 12)
        assertEquals(emptyList<WeatherAlert>(), loud(listOf(next), listOf(record(first))))
        val open = alert("c", AlertKind.Snow, AlertLevel.Severe, 0, null)
        val later = alert("d", AlertKind.Snow, AlertLevel.Severe, 40, 50)
        assertEquals(emptyList<WeatherAlert>(), loud(listOf(later), listOf(record(open))))
    }

    @Test
    fun rememberKeepsAllAlertsOfNotifiedKindsAndDropsExpired() {
        val storm = alert("a", AlertKind.Thunderstorm, AlertLevel.Severe, 0, 4)
        val stormLater = alert("b", AlertKind.Thunderstorm, AlertLevel.Minor, 10, 12)
        val fog = alert("c", AlertKind.Fog, AlertLevel.Minor, 0, 2)
        val old = NotifiedAlert(AlertKind.Heat, 1, at(-30), at(-1))
        val openOld = NotifiedAlert(AlertKind.Snow, 1, at(-49), null)
        val openNew = NotifiedAlert(AlertKind.Snow, 2, at(-47), null)
        val result = AlertNotices.remember(listOf(old, openOld, openNew), listOf(storm, stormLater, fog), posted = listOf(storm), now)
        // Туман не показан (нет разрешения или уже был), поэтому не помечен.
        assertEquals(listOf(openNew, record(storm), record(stormLater)), result)
    }

    @Test
    fun rememberIsLimited() {
        val many = (1..AlertNotices.MAX_RECORDS + 5).map { NotifiedAlert(AlertKind.Wind, 1, at(it.toLong()), at(it + 1L)) }
        val result = AlertNotices.remember(many, emptyList(), emptyList(), now)
        assertEquals(AlertNotices.MAX_RECORDS, result.size)
        assertEquals(many.last(), result.last())
    }

    @Test
    fun recordsSurviveEncodingAndBrokenLinesAreSkipped() {
        val records = listOf(
            NotifiedAlert(AlertKind.Ice, 3, at(1), at(5)),
            NotifiedAlert(AlertKind.Other, 1, at(2), null),
        )
        assertEquals(records, AlertNotices.decode(AlertNotices.encode(records)))
        val broken = AlertNotices.encode(records) + "\nWind|x|1|2\nNoSuchKind|1|1|2\n\nRain|1|1"
        assertEquals(records, AlertNotices.decode(broken))
        assertEquals(emptyList<NotifiedAlert>(), AlertNotices.decode(""))
    }

    @Test
    fun tapOpensTheSameAlertOrTheSameKindOrNothing() {
        val wind = alert("w2", AlertKind.Wind, AlertLevel.Moderate, 0, 6)
        val fog = alert("f", AlertKind.Fog, AlertLevel.Minor, 0, 2)
        assertEquals(fog, AlertNotices.pick(AlertRequest("f", AlertKind.Fog), listOf(wind, fog)))
        assertEquals(wind, AlertNotices.pick(AlertRequest("w1", AlertKind.Wind), listOf(fog, wind)))
        assertNull(AlertNotices.pick(AlertRequest("s", AlertKind.Snow), listOf(wind, fog)))
    }
}
