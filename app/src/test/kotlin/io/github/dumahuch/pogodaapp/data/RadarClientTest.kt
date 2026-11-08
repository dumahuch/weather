package io.github.dumahuch.pogodaapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Адрес запроса и разбор ответа Bright Sky `/radar`. Образец ответа составлен по описанию сервиса
 * (https://api.brightsky.dev/openapi.json): поля и их виды как там, числа придуманы (настоящего ответа из среды разработки
 * не получить, сеть к сервису закрыта).
 */
class RadarClientTest {

    private fun grid(centre: Int, around: Int): String {
        // 5×5: в середине centre, вокруг around; крайний ряд и столбец нули, в разбор не попадают.
        val rows = (0 until 5).joinToString(",") { r ->
            (0 until 5).joinToString(",", "[", "]") { c ->
                when {
                    r == 0 || r == 4 || c == 0 || c == 4 -> "0"
                    r == 2 && c == 2 -> "$centre"
                    else -> "$around"
                }
            }
        }
        return "[$rows]"
    }

    @Test
    fun urlAsksForASmallPlainSquareAroundThePlaceAndTheNextHour() {
        val url = BrightSkyRadarClient().buildUrl(52.3746, 9.7386, Instant.parse("2026-11-08T12:02:30.900Z"))
        assertEquals(
            "https://api.brightsky.dev/radar?lat=52.375&lon=9.739&distance=2000&format=plain&tz=UTC" +
                "&date=2026-11-08T11:47:30Z&last_date=2026-11-08T13:22:30Z",
            url,
        )
    }

    @Test
    fun readsCentreAverageInMmPerHour() {
        // Среднее по 3×3: (8 клеток по 10 + 50) / 9 = 14,44 сотых мм за 5 минут = 0,1733 мм/ч.
        val json = """{"radar":[{"timestamp":"2026-11-08T12:05:00+00:00","source":"x","precipitation_5":${grid(50, 10)}}]}"""
        val frames = RadarParser.parse(json)
        assertNotNull(frames)
        assertEquals(1, frames!!.size)
        assertEquals(Instant.parse("2026-11-08T12:05:00Z"), frames[0].time)
        assertEquals(14.4444 * 0.12, frames[0].mmPerHour, 0.001)
    }

    @Test
    fun heavyRainConvertsUnits() {
        // 45 сотых мм за пять минут во всех клетках = 0,45 мм за 5 минут = 5,4 мм/ч.
        val json = """{"radar":[{"timestamp":"2026-11-08T12:10:00+01:00","precipitation_5":${grid(45, 45)}}]}"""
        val frame = RadarParser.parse(json)!!.single()
        assertEquals(Instant.parse("2026-11-08T11:10:00Z"), frame.time)
        assertEquals(5.4, frame.mmPerHour, 0.001)
    }

    @Test
    fun skipsBrokenFramesButKeepsTheRest() {
        val json = """{"radar":[
            {"timestamp":"не время","precipitation_5":${grid(1, 1)}},
            {"timestamp":"2026-11-08T12:15:00+00:00"},
            {"timestamp":"2026-11-08T12:20:00+00:00","precipitation_5":[]},
            {"timestamp":"2026-11-08T12:25:00+00:00","precipitation_5":${grid(0, 0)}}
        ]}"""
        val frames = RadarParser.parse(json)!!
        assertEquals(listOf(Instant.parse("2026-11-08T12:25:00Z")), frames.map { it.time })
        assertEquals(0.0, frames[0].mmPerHour, 0.0)
    }

    @Test
    fun emptyCellsCountAsDry() {
        val json = """{"radar":[{"timestamp":"2026-11-08T12:05:00+00:00","precipitation_5":[[null,null,null],[null,100,null],[null,null,null]]}]}"""
        assertEquals(100.0 / 9 * 0.12, RadarParser.parse(json)!!.single().mmPerHour, 0.001)
    }

    @Test
    fun brokenResponsesGiveNull() {
        assertNull(RadarParser.parse("не json"))
        assertNull(RadarParser.parse("""{"other":1}"""))
        assertNull(RadarParser.parse("[".repeat(100)))
    }
}
