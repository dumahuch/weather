package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Чужой ответ нельзя считать надёжным: пустой, оборванный, не тот тип, огромные числа, вложенность. Разборщики
 * должны либо отдать пустой результат, либо [ForecastException]; никакое другое исключение не должно дойти до экрана.
 */
class ParserRobustnessTest {

    private val garbage = listOf(
        "",
        " ",
        "null",
        "[]",
        "{}",
        "{",
        """{"hourly":""",
        """{"hourly":null,"daily":null,"current":null,"timezone":null}""",
        """{"hourly":[],"daily":[],"current":[],"timezone":5}""",
        """{"timezone":"Not/AZone","current":{},"daily":{},"hourly":{}}""",
        """{"error":true}""",
        """{"results":"nope"}""",
        """{"results":[null,1,"x",[],{"name":5,"latitude":"a","longitude":{}}]}""",
        """{"content":[{"partregion_id":"x","Pollen":5}],"last_update":"31.02.2026"}""",
        """{"content":null}""",
        """{"hourly":{"time":[1e999],"european_aqi":["a"]}}""",
        "\u0000\u0001\u0002",
        "x".repeat(200_000),
        "[".repeat(5_000),
        """{"a":""" + "[".repeat(2_000),
    )

    private fun expectOnlyForecastExceptionOrEmpty(name: String, block: () -> Any?) {
        try {
            block()
        } catch (e: ForecastException) {
            // допустимо: ответ отвергнут понятной ошибкой
        } catch (e: Throwable) {
            fail("$name: неожиданное исключение ${e::class.simpleName}: ${e.message}")
        }
    }

    @Test
    fun forecastParserNeverThrowsAnythingButForecastException() {
        garbage.forEach { text -> expectOnlyForecastExceptionOrEmpty("прогноз «${text.take(20)}»") { OpenMeteoParser.parse(text) } }
    }

    @Test
    fun geocodingParserNeverThrowsAnythingButForecastException() {
        garbage.forEach { text -> expectOnlyForecastExceptionOrEmpty("поиск «${text.take(20)}»") { GeocodingParser.parse(text) } }
    }

    @Test
    fun airQualityParserNeverThrows() {
        garbage.forEach { text ->
            try {
                AirQualityParser.parse(text, Instant.parse("2026-11-02T12:00:00Z"))
            } catch (e: Throwable) {
                fail("воздух «${text.take(20)}»: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    @Test
    fun pollenParserNeverThrows() {
        garbage.forEach { text ->
            try {
                PollenParser.parse(text, 32, LocalDate.of(2026, 11, 2))
            } catch (e: Throwable) {
                fail("пыльца «${text.take(20)}»: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    @Test
    fun alertsParserNeverThrows() {
        val alertsGarbage = garbage + listOf(
            """{"alerts":null}""",
            """{"alerts":[{"alert_id":5,"severity":7,"onset":[],"headline_de":{}}],"location":"x"}""",
            """{"alerts":[{"id":1e999,"severity":"minor","onset":"2026-13-45T99:00:00+00:00","headline_de":"X"}]}""",
            """{"alerts":[{"alert_id":"a","severity":"minor","onset":"2026-10-28T10:00:00+00:00","expires":"bald","headline_en":"X"}]}""",
        )
        alertsGarbage.forEach { text ->
            try {
                AlertsParser.parse(text)
            } catch (e: Throwable) {
                fail("предупреждения «${text.take(20)}»: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    @Test
    fun garbageGivesNoPollenAndNoAir() {
        assertNull(PollenParser.parse("{}", 32, LocalDate.of(2026, 11, 2)))
        assertNull(AirQualityParser.parse("{}", Instant.parse("2026-11-02T12:00:00Z")))
    }
}

class JsonGuardTest {

    @Test
    fun realAnswersAreNotTooDeep() {
        val sample = javaClass.classLoader!!.getResourceAsStream("open-meteo/hannover-15d.json")!!.readBytes().decodeToString()
        org.junit.Assert.assertFalse(JsonGuard.tooDeep(sample))
    }

    @Test
    fun deepNestingIsDetected() {
        org.junit.Assert.assertTrue(JsonGuard.tooDeep("[".repeat(33)))
        org.junit.Assert.assertTrue(JsonGuard.tooDeep("""{"a":""" + "[".repeat(40)))
        org.junit.Assert.assertFalse(JsonGuard.tooDeep("[".repeat(32)))
    }

    @Test
    fun bracketsInsideStringsDoNotCount() {
        org.junit.Assert.assertFalse(JsonGuard.tooDeep("{\"a\":\"" + "[".repeat(100) + "\"}"))
        org.junit.Assert.assertFalse(JsonGuard.tooDeep("{\"a\":\"\\\"" + "[".repeat(100) + "\"}"))
    }
}
