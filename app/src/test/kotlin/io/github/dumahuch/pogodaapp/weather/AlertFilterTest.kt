package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertFilterTest {
    private fun alert(kind: AlertKind, level: AlertLevel) = WeatherAlert(
        id = "x",
        level = level,
        kind = kind,
        onset = Instant.EPOCH,
        expires = null,
        issued = null,
        headlineDe = "",
        headlineEn = "",
        descriptionDe = "",
        descriptionEn = "",
        instructionDe = null,
        instructionEn = null,
    )

    @Test
    fun defaultsKeepFrostFogHeatAndTheirKindAwayButNotFromSevereWeather() {
        val filter = AlertFilter()
        assertEquals(2, filter.minLevel)
        assertEquals(setOf(AlertKind.Thunderstorm, AlertKind.Wind, AlertKind.Rain, AlertKind.Snow, AlertKind.Ice), filter.kinds)
        listOf(AlertKind.Frost, AlertKind.Fog, AlertKind.Heat, AlertKind.Uv, AlertKind.Thaw, AlertKind.Other).forEach {
            assertFalse(filter.accepts(alert(it, AlertLevel.Moderate)))
            assertTrue(filter.accepts(alert(it, AlertLevel.Severe)))
            assertTrue(filter.accepts(alert(it, AlertLevel.Extreme)))
        }
    }

    @Test
    fun minLevelCutsMilderAlertsOfChosenKinds() {
        assertFalse(AlertFilter().accepts(alert(AlertKind.Wind, AlertLevel.Minor)))
        assertTrue(AlertFilter().accepts(alert(AlertKind.Wind, AlertLevel.Moderate)))
        assertTrue(AlertFilter(minLevel = 1).accepts(alert(AlertKind.Wind, AlertLevel.Minor)))
    }

    @Test
    fun kindsSurviveEncodingAndUnknownNamesAreSkipped() {
        val kinds = setOf(AlertKind.Heat, AlertKind.Wind)
        assertEquals(kinds, AlertFilter.decodeKinds(AlertFilter.encodeKinds(kinds)))
        assertEquals(AlertFilter.DEFAULT_KINDS, AlertFilter.decodeKinds(null))
        assertEquals(emptySet<AlertKind>(), AlertFilter.decodeKinds(""))
        assertEquals(setOf(AlertKind.Fog), AlertFilter.decodeKinds("Fog,Nonsense"))
    }

    @Test
    fun storedLevelOutsideOneOrTwoFallsBackToDefault() {
        assertEquals(1, AlertFilter.levelOf("1"))
        assertEquals(2, AlertFilter.levelOf("2"))
        assertEquals(2, AlertFilter.levelOf(null))
        assertEquals(2, AlertFilter.levelOf("3"))
        assertEquals(2, AlertFilter.levelOf("x"))
    }
}
