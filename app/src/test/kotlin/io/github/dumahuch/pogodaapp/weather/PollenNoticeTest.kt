package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Уведомление о пыльце: порог «2-3», выбранные виды, часы и частота проверок. */
class PollenNoticeTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 4, 20)

    private fun forecast(vararg levels: Pair<PollenKind, String>): PollenForecast {
        val map = levels.associate { (kind, text) -> kind to listOf(PollenLevel.parse(text), PollenLevel.parse("0"), PollenLevel.parse("0")) }
        return PollenForecast(1, today, map)
    }

    @Test
    fun onlySelectedKindsFromThresholdUpAreReported() {
        val f = forecast(
            PollenKind.Birch to "2-3",
            PollenKind.Grass to "2",
            PollenKind.Mugwort to "3",
            PollenKind.Hazel to "3",
        )
        val hits = PollenNotice.hits(f, today, PollenNotice.DEFAULT_KINDS)
        // Злаки ниже порога, лещина не выбрана; сильнее раньше.
        assertEquals(listOf(PollenKind.Mugwort, PollenKind.Birch), hits.map { it.first })
        assertEquals(PollenLevel(6), hits[0].second)
        assertTrue(PollenNotice.hits(f, today, emptySet()).isEmpty())
        assertTrue(PollenNotice.hits(f, today.plusDays(5), PollenNotice.DEFAULT_KINDS).isEmpty())
    }

    @Test
    fun equalLevelsFollowTheMainAllergenOrder() {
        val f = forecast(PollenKind.Ragweed to "3", PollenKind.Birch to "3", PollenKind.Grass to "3")
        assertEquals(
            listOf(PollenKind.Birch, PollenKind.Grass, PollenKind.Ragweed),
            PollenNotice.hits(f, today, PollenNotice.DEFAULT_KINDS).map { it.first },
        )
    }

    @Test
    fun checksOnlyBetweenSevenAndNine() {
        fun at(hour: Int, minute: Int = 0) = LocalDate.of(2026, 4, 20).atTime(hour, minute).atZone(zone).toInstant()
        assertFalse(PollenNotice.due(at(6, 59), zone, null, null))
        assertTrue(PollenNotice.due(at(7), zone, null, null))
        assertTrue(PollenNotice.due(at(20, 59), zone, null, null))
        assertFalse(PollenNotice.due(at(21), zone, null, null))
    }

    @Test
    fun oneNoticeADayAndAtMostOneCheckEveryThreeHours() {
        val now = Instant.parse("2026-04-20T09:00:00Z")
        assertFalse(PollenNotice.due(now, zone, null, LocalDate.of(2026, 4, 20)))
        assertTrue(PollenNotice.due(now, zone, null, LocalDate.of(2026, 4, 19)))
        assertFalse(PollenNotice.due(now, zone, now.minusSeconds(2 * 3600), null))
        assertTrue(PollenNotice.due(now, zone, now.minusSeconds(3 * 3600), null))
        // Часы ушли назад: запись о проверке «из будущего» не держит.
        assertTrue(PollenNotice.due(now, zone, now.plusSeconds(600), null))
    }

    @Test
    fun keysRoundTripAndDefaultsApplyOnlyWhenNothingWasChosen() {
        assertEquals(PollenNotice.DEFAULT_KINDS, PollenNotice.decodeKinds(null))
        assertEquals(emptySet<PollenKind>(), PollenNotice.decodeKinds(""))
        val chosen = setOf(PollenKind.Hazel, PollenKind.Rye)
        assertEquals("Hazel,Rye", PollenNotice.encodeKinds(chosen))
        assertEquals(chosen, PollenNotice.decodeKinds("Hazel,Rye"))
        assertEquals(setOf(PollenKind.Ash), PollenNotice.decodeKinds("Ash,Unknown"))
    }
}
