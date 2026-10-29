package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PollenTest {

    private val issuedDay = LocalDate.of(2026, 10, 28)

    /** Образец по описанию DWD (s31fg.json): один район с частями (Рейн-Майн) и один без (Мекленбург-Передняя Померания). */
    private val sample = """
        {
          "name": "Pollenflug-Gefahrenindex für Deutschland ausgegeben vom Deutschen Wetterdienst",
          "last_update": "2026-10-28 11:00 Uhr",
          "next_update": "2026-10-29 11:00 Uhr",
          "legend": {"id1": "0", "id1_desc": "keine Belastung"},
          "content": [
            {
              "partregion_id": 92, "region_id": 90, "region_name": "Hessen", "partregion_name": "Rhein-Main",
              "Pollen": {
                "Hasel": {"today": "0", "tomorrow": "0-1", "dayafter_to": "1"},
                "Erle": {"today": "0-1", "tomorrow": "1", "dayafter_to": "1-2"},
                "Esche": {"today": "0", "tomorrow": "0", "dayafter_to": "0"},
                "Birke": {"today": "2", "tomorrow": "2-3", "dayafter_to": "3"},
                "Graeser": {"today": "1-2", "tomorrow": "1", "dayafter_to": "0-1"},
                "Roggen": {"today": "0", "tomorrow": "0", "dayafter_to": "0"},
                "Beifuss": {"today": "0", "tomorrow": "0", "dayafter_to": "0"},
                "Ambrosia": {"today": "0", "tomorrow": "0", "dayafter_to": "0"}
              }
            },
            {
              "partregion_id": -1, "region_id": 20, "region_name": "Mecklenburg-Vorpommern", "partregion_name": "",
              "Pollen": {
                "Hasel": {"today": "3", "tomorrow": "3", "dayafter_to": "2-3"},
                "Birke": {"today": "0", "tomorrow": "0", "dayafter_to": "0"}
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun readsAPartRegionByItsPartRegionNumber() {
        val forecast = PollenParser.parse(sample, 92, issuedDay)
        assertNotNull(forecast)
        assertEquals(92, forecast!!.regionKey)
        assertEquals(issuedDay, forecast.issued)
        val today = forecast.on(issuedDay)!!
        assertEquals(PollenLevel(4), today[PollenKind.Birch])
        assertEquals(PollenLevel(3), today[PollenKind.Grass])
        assertEquals(PollenLevel(1), today[PollenKind.Alder])
        assertEquals(PollenLevel(0), today[PollenKind.Hazel])
    }

    @Test
    fun readsARegionWithoutPartsByItsRegionNumber() {
        val forecast = PollenParser.parse(sample, 20, issuedDay)
        assertEquals(PollenLevel(6), forecast?.on(issuedDay)?.get(PollenKind.Hazel))
    }

    @Test
    fun regionNumberOfAParentIsNotAnAnswerForItsParts() {
        // 90 это земля Гессен, у неё есть части: районом пыльцы она не является.
        assertNull(PollenParser.parse(sample, 90, issuedDay))
        assertNull(PollenParser.parse(sample, 12345, issuedDay))
    }

    @Test
    fun kindsMissingInTheFileAreSimplyAbsent() {
        val day = PollenParser.parse(sample, 20, issuedDay)!!.on(issuedDay)!!
        assertEquals(setOf(PollenKind.Hazel, PollenKind.Birch), day.keys)
    }

    @Test
    fun issueDayComesFromLastUpdateNotFromTheFetchDay() {
        val forecast = PollenParser.parse(sample, 92, LocalDate.of(2026, 10, 29))
        assertEquals(issuedDay, forecast?.issued)
    }

    @Test
    fun withoutLastUpdateTheFetchDayIsTheIssueDay() {
        val json = sample.replace("\"last_update\": \"2026-10-28 11:00 Uhr\",", "")
        assertEquals(LocalDate.of(2026, 10, 30), PollenParser.parse(json, 92, LocalDate.of(2026, 10, 30))?.issued)
    }

    @Test
    fun brokenFileGivesNothing() {
        assertNull(PollenParser.parse("не json", 92, issuedDay))
        assertNull(PollenParser.parse("{}", 92, issuedDay))
        assertNull(PollenParser.parse("""{"content":[{"partregion_id":92,"Pollen":{}}]}""", 92, issuedDay))
    }

    @Test
    fun levelsMapToHalfSteps() {
        val expected = listOf("0" to 0, "0-1" to 1, "1" to 2, "1-2" to 3, "2" to 4, "2-3" to 5, "3" to 6)
        for ((text, halves) in expected) assertEquals(text, halves, PollenLevel.parse(text)?.halves)
        assertEquals(PollenLevel(5).whole, 2)
        assertTrue(PollenLevel(5).isBetween)
        assertFalse(PollenLevel(4).isBetween)
    }

    @Test
    fun unknownLevelTextIsNotALevel() {
        for (text in listOf("-1", "", "4", "high", "1-3", null)) assertNull(text, PollenLevel.parse(text))
    }

    @Test
    fun dayOffsetsFollowTheIssueDay() {
        val forecast = PollenParser.parse(sample, 92, issuedDay)!!
        // Сегодня, завтра, послезавтра по файлу.
        assertEquals(PollenLevel(4), forecast.on(issuedDay)!![PollenKind.Birch])
        assertEquals(PollenLevel(5), forecast.on(issuedDay.plusDays(1))!![PollenKind.Birch])
        assertEquals(PollenLevel(6), forecast.on(issuedDay.plusDays(2))!![PollenKind.Birch])
        // Дальше трёх суток и раньше дня выпуска файл ничего не говорит.
        assertNull(forecast.on(issuedDay.plusDays(3)))
        assertNull(forecast.on(issuedDay.minusDays(1)))
    }

    @Test
    fun shownKindsAreTheFourStrongest() {
        val forecast = PollenParser.parse(sample, 92, issuedDay)!!
        val shown = forecast.shown(issuedDay)!!
        assertEquals(
            listOf(PollenKind.Birch, PollenKind.Grass, PollenKind.Alder, PollenKind.Mugwort),
            shown.map { it.first },
        )
        assertEquals(listOf(4, 3, 1, 0), shown.map { it.second.halves })
    }

    @Test
    fun withoutAnyLoadTheFourMainAllergensAreShownInMockupOrder() {
        val json = """{"last_update":"2026-10-28 11:00 Uhr","content":[{"partregion_id":-1,"region_id":20,"Pollen":{
            "Hasel":{"today":"0","tomorrow":"0","dayafter_to":"0"},"Erle":{"today":"0","tomorrow":"0","dayafter_to":"0"},
            "Esche":{"today":"0","tomorrow":"0","dayafter_to":"0"},"Birke":{"today":"0","tomorrow":"0","dayafter_to":"0"},
            "Graeser":{"today":"0","tomorrow":"0","dayafter_to":"0"},"Roggen":{"today":"0","tomorrow":"0","dayafter_to":"0"},
            "Beifuss":{"today":"0","tomorrow":"0","dayafter_to":"0"},"Ambrosia":{"today":"0","tomorrow":"0","dayafter_to":"0"}}}]}"""
        val shown = PollenParser.parse(json, 20, issuedDay)!!.shown(issuedDay)!!
        assertEquals(
            listOf(PollenKind.Birch, PollenKind.Grass, PollenKind.Mugwort, PollenKind.Ragweed),
            shown.map { it.first },
        )
    }

    @Test
    fun dwdKeysMatchTheFileNames() {
        assertEquals(
            listOf("Hasel", "Erle", "Esche", "Birke", "Graeser", "Roggen", "Beifuss", "Ambrosia"),
            PollenKind.entries.map { it.dwdKey },
        )
    }
}
