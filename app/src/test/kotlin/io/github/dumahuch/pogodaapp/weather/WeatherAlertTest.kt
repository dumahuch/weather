package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WeatherAlertTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** 12:20 по Берлину, 28 октября 2026 (летнее время уже кончилось: UTC+1). */
    private val now = Instant.parse("2026-10-28T11:20:00Z")

    private fun alert(
        id: String,
        level: AlertLevel = AlertLevel.Minor,
        kind: AlertKind = AlertKind.Other,
        onset: String = "2026-10-28T10:00:00Z",
        expires: String? = "2026-10-28T18:00:00Z",
    ) = WeatherAlert(
        id = id,
        level = level,
        kind = kind,
        onset = Instant.parse(onset),
        expires = expires?.let(Instant::parse),
        issued = null,
        headlineDe = "H",
        headlineEn = "H",
        descriptionDe = "",
        descriptionEn = "",
        instructionDe = null,
        instructionEn = null,
    )

    @Test
    fun kindsFromGermanEventNames() {
        assertEquals(AlertKind.Thunderstorm, AlertKinds.classify("STARKES GEWITTER", null))
        assertEquals(AlertKind.Thunderstorm, AlertKinds.classify("SCHWERES GEWITTER mit HEFTIGEM STARKREGEN", null))
        assertEquals(AlertKind.Wind, AlertKinds.classify("WINDBÖEN", null))
        assertEquals(AlertKind.Wind, AlertKinds.classify("ORKANARTIGE BÖEN", null))
        assertEquals(AlertKind.Wind, AlertKinds.classify("STURMBÖEN", null))
        assertEquals(AlertKind.Rain, AlertKinds.classify("DAUERREGEN", null))
        assertEquals(AlertKind.Rain, AlertKinds.classify("HEFTIGER STARKREGEN", null))
        assertEquals(AlertKind.Snow, AlertKinds.classify("LEICHTER SCHNEEFALL", null))
        assertEquals(AlertKind.Snow, AlertKinds.classify("SCHNEEVERWEHUNG", null))
        assertEquals(AlertKind.Frost, AlertKinds.classify("STRENGER FROST", null))
        assertEquals(AlertKind.Ice, AlertKinds.classify("GLÄTTE", null))
        assertEquals(AlertKind.Ice, AlertKinds.classify("GLATTEIS", null))
        assertEquals(AlertKind.Fog, AlertKinds.classify("NEBEL", null))
        assertEquals(AlertKind.Heat, AlertKinds.classify("STARKE HITZE", null))
        assertEquals(AlertKind.Uv, AlertKinds.classify("UV-INDEX", null))
        assertEquals(AlertKind.Thaw, AlertKinds.classify("TAUWETTER", null))
        assertEquals(AlertKind.Other, AlertKinds.classify("LEITERSEILSCHWINGUNGEN", null))
    }

    @Test
    fun englishNameWhenGermanIsMissing() {
        assertEquals(AlertKind.Wind, AlertKinds.classify(null, "wind gusts"))
        assertEquals(AlertKind.Thunderstorm, AlertKinds.classify("", "heavy thunderstorms"))
        assertEquals(AlertKind.Other, AlertKinds.classify(null, null))
    }

    @Test
    fun severityLevels() {
        assertEquals(AlertLevel.Minor, AlertLevel.ofSeverity("minor"))
        assertEquals(AlertLevel.Extreme, AlertLevel.ofSeverity("Extreme"))
        assertEquals(null, AlertLevel.ofSeverity("unknown"))
        assertEquals(null, AlertLevel.ofSeverity(null))
    }

    @Test
    fun shownAtDropsExpiredAndPutsSeriousFirst() {
        val set = AlertSet(
            area = null,
            alerts = listOf(
                alert("old", AlertLevel.Extreme, expires = "2026-10-28T11:00:00Z"),
                alert("minor-late", AlertLevel.Minor, AlertKind.Wind, onset = "2026-10-28T15:00:00Z"),
                alert("minor-early", AlertLevel.Minor, onset = "2026-10-28T09:00:00Z"),
                alert("severe", AlertLevel.Severe),
                alert("open", AlertLevel.Moderate, expires = null),
            ),
        )
        assertEquals(listOf("severe", "open", "minor-early", "minor-late"), set.shownAt(now).map { it.id })
        assertFalse(alert("x", expires = "2026-10-28T11:20:00Z").activeAt(now))
        assertTrue(alert("x", expires = null).activeAt(now))
    }

    @Test
    fun `consecutive pieces of one warning are shown as one`() {
        // Два куска заморозков: до 12:30 и с 12:30 (Дима 27.11; «сейчас» 12:20, первый ещё идёт); конец у позднего, начало и номер у раннего.
        val first = alert("a", onset = "2026-10-28T03:00:00Z", expires = "2026-10-28T11:30:00Z")
        val second = alert("b", onset = "2026-10-28T11:30:00Z", expires = "2026-10-28T16:00:00Z")
        val shown = AlertSet(null, listOf(second, first)).shownAt(now)
        assertEquals(1, shown.size)
        assertEquals("a", shown[0].id)
        assertEquals(first.onset, shown[0].onset)
        assertEquals(second.expires, shown[0].expires)
        // Пауза до часа ещё склеивается, больше часа нет.
        val near = alert("c", onset = "2026-10-28T12:30:00Z", expires = "2026-10-28T16:00:00Z")
        assertEquals(1, AlertSet(null, listOf(first, near)).shownAt(now).size)
        val far = alert("d", onset = "2026-10-28T12:31:00Z", expires = "2026-10-28T16:00:00Z")
        assertEquals(2, AlertSet(null, listOf(first, far)).shownAt(now).size)
    }

    @Test
    fun `different level or kind is not merged and an open end stays open`() {
        val first = alert("a", onset = "2026-10-28T03:00:00Z", expires = "2026-10-28T11:30:00Z")
        val higher = alert("b", AlertLevel.Moderate, onset = "2026-10-28T11:30:00Z")
        val other = alert("c", kind = AlertKind.Wind, onset = "2026-10-28T11:30:00Z")
        assertEquals(3, AlertSet(null, listOf(first, higher, other)).shownAt(now).size)
        val open = alert("d", onset = "2026-10-28T11:30:00Z", expires = null)
        assertEquals(null, AlertSet(null, listOf(first, open)).shownAt(now).single().expires)
    }

    @Test
    fun whenTexts() {
        // Уже идёт и кончится сегодня в 19:00 по Берлину.
        val until = AlertWhen.of(alert("a"), now, berlin) as AlertWhen.Until
        assertEquals(0, until.days)
        assertEquals(Instant.parse("2026-10-28T18:00:00Z"), until.at)
        // Кончится завтра.
        assertEquals(1, (AlertWhen.of(alert("b", expires = "2026-10-29T05:00:00Z"), now, berlin) as AlertWhen.Until).days)
        // Конец не известен.
        assertEquals(AlertWhen.Ongoing, AlertWhen.of(alert("c", expires = null), now, berlin))
        // Начнётся через 22 минуты: округляется вверх до пяти.
        assertEquals(AlertWhen.StartsIn(25), AlertWhen.of(alert("d", onset = "2026-10-28T11:42:00Z"), now, berlin))
        assertEquals(AlertWhen.StartsIn(5), AlertWhen.of(alert("e", onset = "2026-10-28T11:20:30Z"), now, berlin))
        // Начнётся через два часа и послезавтра.
        assertEquals(0, (AlertWhen.of(alert("f", onset = "2026-10-28T13:20:00Z"), now, berlin) as AlertWhen.From).days)
        assertEquals(2, (AlertWhen.of(alert("g", onset = "2026-10-30T08:00:00Z", expires = null), now, berlin) as AlertWhen.From).days)
    }

    @Test
    fun calendarDaysFollowThePlaceZone() {
        // 23:30 по Гринвичу это уже 0:30 следующих суток в Берлине.
        val late = Instant.parse("2026-10-28T23:30:00Z")
        assertEquals(1, AlertWhen.daysBetween(now, late, berlin))
        assertEquals(0, AlertWhen.daysBetween(now, late, ZoneId.of("UTC")))
    }
}
