package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Разбор ответа Bright Sky `/alerts`. Образец `brightsky/alerts-hannover.json` составлен по описанию сервиса
 * (https://api.brightsky.dev/openapi.json): поля и их виды как там, тексты и числа придуманы (настоящего ответа из среды
 * разработки не получить, сеть к сервису закрыта).
 */
class AlertsParserTest {

    private val fixture: String by lazy {
        checkNotNull(javaClass.classLoader?.getResource("brightsky/alerts-hannover.json")).readText(Charsets.UTF_8)
    }

    @Test
    fun readsAlertsAndArea() {
        val set = AlertsParser.parse(fixture)
        assertNotNull(set)
        assertEquals("Stadt Hannover", set!!.area)
        // Учебное сообщение (status test) и отбой (allclear) пропущены.
        assertEquals(listOf("wind-1", "storm-1", "rain-1"), set.alerts.map { it.id.substringAfterLast('.') })
    }

    @Test
    fun readsFieldsOfOneAlert() {
        val storm = AlertsParser.parse(fixture)!!.alerts.first { it.id.endsWith("storm-1") }
        assertEquals(AlertLevel.Severe, storm.level)
        assertEquals(AlertKind.Thunderstorm, storm.kind)
        assertEquals(Instant.parse("2026-10-28T14:00:00Z"), storm.onset)
        assertEquals(Instant.parse("2026-10-28T20:00:00Z"), storm.expires)
        assertEquals(Instant.parse("2026-10-28T10:30:00Z"), storm.issued)
        assertEquals("Amtliche UNWETTERWARNUNG vor SCHWEREM GEWITTER mit HAGEL", storm.headlineDe)
        assertEquals("Official SEVERE WARNING of HEAVY THUNDERSTORMS with HAIL", storm.headlineEn)
        assertTrue(storm.instructionDe!!.startsWith("Hinweis"))
    }

    @Test
    fun nullFieldsStayEmptyInsteadOfTheWordNull() {
        val set = AlertsParser.parse(fixture)!!
        val wind = set.alerts.first { it.id.endsWith("wind-1") }
        assertNull(wind.instructionDe)
        assertNull(wind.instructionEn)
        val rain = set.alerts.first { it.id.endsWith("rain-1") }
        assertNull(rain.expires)
        assertEquals(AlertLevel.Moderate, rain.level)
        assertEquals(AlertKind.Rain, rain.kind)
    }

    @Test
    fun noAlertsAndNoLocation() {
        val set = AlertsParser.parse("""{"alerts":[],"location":null}""")
        assertNotNull(set)
        assertTrue(set!!.alerts.isEmpty())
        assertNull(set.area)
    }

    @Test
    fun entriesWithoutLevelOrStartAreSkipped() {
        val json = """{"alerts":[
            {"alert_id":"a","severity":"unknown","onset":"2026-10-28T10:00:00+00:00","headline_de":"X"},
            {"alert_id":"b","severity":"minor","onset":"gestern","headline_de":"X"},
            {"alert_id":"c","severity":"minor","onset":"2026-10-28T10:00:00+00:00"},
            {"alert_id":"d","severity":"minor","onset":"2026-10-28T10:00:00+00:00","headline_de":"Gut"},
            {"alert_id":"d","severity":"minor","onset":"2026-10-28T10:00:00+00:00","headline_de":"Doppelt"},
            "мусор", 5, null
        ]}"""
        val set = AlertsParser.parse(json)!!
        assertEquals(listOf("d"), set.alerts.map { it.id })
        assertEquals("Gut", set.alerts.single().headlineDe)
        // Нет английского текста: берётся немецкий, чтобы шторка не была пустой.
        assertEquals("Gut", set.alerts.single().headlineEn)
    }

    @Test
    fun brokenAnswersGiveNothing() {
        assertNull(AlertsParser.parse(""))
        assertNull(AlertsParser.parse("не json"))
        assertNull(AlertsParser.parse("""{"alerts":"нет"}"""))
        assertNull(AlertsParser.parse("""{"detail":"Not Found"}"""))
        assertNull(AlertsParser.parse("[".repeat(10_000)))
    }

    @Test
    fun readsAtMostFiftyEntries() {
        val one = """{"alert_id":"%d","severity":"minor","onset":"2026-10-28T10:00:00+00:00","headline_de":"X"}"""
        val json = """{"alerts":[${(1..80).joinToString(",") { one.format(it) }}]}"""
        assertEquals(AlertsParser.MAX_ALERTS, AlertsParser.parse(json)!!.alerts.size)
    }

    @Test
    fun urlHasCoordinatesToThreeDecimalsOnly() {
        val url = BrightSkyAlertsClient().buildUrl(52.374512, 9.738633)
        assertEquals("https://api.brightsky.dev/alerts?lat=52.375&lon=9.739", url)
    }
}
