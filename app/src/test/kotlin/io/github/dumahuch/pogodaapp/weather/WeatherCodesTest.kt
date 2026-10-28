package io.github.dumahuch.pogodaapp.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {

    private fun assertAll(condition: Condition, vararg codes: Int) {
        for (code in codes) {
            assertEquals("код $code", condition, WeatherCodes.toCondition(code))
        }
    }

    @Test
    fun clearCodes() = assertAll(Condition.Clear, 0, 1)

    @Test
    fun partlyCloudy() = assertAll(Condition.Partly, 2)

    @Test
    fun overcast() = assertAll(Condition.Cloudy, 3)

    @Test
    fun fog() = assertAll(Condition.Fog, 45, 48)

    @Test
    fun drizzleRainAndShowersAreRain() =
        assertAll(Condition.Rain, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)

    @Test
    fun snowSnowGrainsAndSnowShowersAreSnow() = assertAll(Condition.Snow, 71, 73, 75, 77, 85, 86)

    @Test
    fun allThunderstormsAreStorm() = assertAll(Condition.Storm, 95, 96, 97, 99)

    @Test
    fun unknownCodeFallsBackToCloudy() = assertAll(Condition.Cloudy, -1, 4, 44, 100, 1000)

    @Test
    fun everyDocumentedCodeIsMappedExplicitly() {
        // Коды WMO из таблицы Open-Meteo. Любой из них, кроме 3 и неизвестных, не должен попасть в «пасмурно» по умолчанию.
        val documented = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        val cloudy = documented.filter { WeatherCodes.toCondition(it) == Condition.Cloudy }
        assertEquals(listOf(3), cloudy)
    }
}
