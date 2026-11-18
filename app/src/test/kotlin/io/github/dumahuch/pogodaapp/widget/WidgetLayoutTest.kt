package io.github.dumahuch.pogodaapp.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {

    @Test
    fun cellsFollowTheAndroidSizeRule() {
        assertEquals(1, WidgetLayouts.cells(40))
        assertEquals(2, WidgetLayouts.cells(110))
        assertEquals(3, WidgetLayouts.cells(180))
        assertEquals(4, WidgetLayouts.cells(250))
        assertEquals(5, WidgetLayouts.cells(320))
    }

    @Test
    fun launcherRoundingDoesNotChangeTheCellCount() {
        assertEquals(4, WidgetLayouts.cells(245))
        assertEquals(4, WidgetLayouts.cells(262))
        assertEquals(2, WidgetLayouts.cells(104))
        assertEquals(1, WidgetLayouts.cells(10))
    }

    @Test
    fun rowsFollowThePixelCellHeight() {
        assertEquals(1, WidgetLayouts.rowCells(94))
        assertEquals(1, WidgetLayouts.rowCells(120))
        assertEquals(2, WidgetLayouts.rowCells(196))
        assertEquals(3, WidgetLayouts.rowCells(298))
        assertEquals(4, WidgetLayouts.rowCells(400))
        assertEquals(1, WidgetLayouts.rowCells(10))
    }

    @Test
    fun layoutByPixelCells() {
        assertEquals(WidgetLayout.Tiny, WidgetLayouts.pick(62, 94))
        assertEquals(WidgetLayout.Narrow, WidgetLayouts.pick(148, 94))
        assertEquals(WidgetLayout.Strip, WidgetLayouts.pick(310, 94))
        assertEquals(WidgetLayout.Tall, WidgetLayouts.pick(62, 196))
        assertEquals(WidgetLayout.Tall, WidgetLayouts.pick(62, 400))
        assertEquals(WidgetLayout.Square, WidgetLayouts.pick(148, 196))
        assertEquals(WidgetLayout.Square, WidgetLayouts.pick(148, 298))
        assertEquals(WidgetLayout.Wide, WidgetLayouts.pick(310, 196))
        assertEquals(WidgetLayout.Wide, WidgetLayouts.pick(310, 298))
        assertEquals(WidgetLayout.Large, WidgetLayouts.pick(310, 400))
    }

    @Test
    fun designSizeFollowsTheMockupGrid() {
        assertEquals(214f, WidgetLayout.Tiny.designWidth, 0f)
        assertEquals(444f, WidgetLayout.Square.designWidth, 0f)
        assertEquals(904f, WidgetLayout.Large.designWidth, 0f)
        assertEquals(904f, WidgetLayout.Large.designHeight, 0f)
        assertEquals(214f, WidgetLayout.Strip.designHeight, 0f)
        assertEquals(214f, WidgetLayout.Tall.designWidth, 0f)
        assertEquals(444f, WidgetLayout.Tall.designHeight, 0f)
    }

    private fun model(ageMinutes: Long) = WidgetModel(
        placeName = "Ганновер", zone = java.time.ZoneOffset.UTC, temperature = 6.0, feelsLike = 4.0,
        condition = io.github.dumahuch.pogodaapp.weather.Condition.Cloudy, drizzle = false, isDay = true, windy = false,
        high = 9.0, low = 3.0, caption = io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption.None, days = emptyList(), ranges = emptyList(),
        windKmh = null, humidity = null, precipitationMm = null, latitude = 52.0, longitude = 9.0, now = java.time.Instant.EPOCH,
        hourPrecipitationMm = null, cloudCoverPercent = null, dataAge = java.time.Duration.ofMinutes(ageMinutes),
    )

    @Test
    fun ageLabelOnlyInBigLayoutsAndOnlyForDataOlderThanAnHour() {
        assertEquals(null, WidgetLayouts.ageLabelHours(WidgetLayout.Square, model(60)))
        assertEquals(1, WidgetLayouts.ageLabelHours(WidgetLayout.Square, model(61)))
        assertEquals(18, WidgetLayouts.ageLabelHours(WidgetLayout.Wide, model(18 * 60 + 5)))
        assertEquals(5, WidgetLayouts.ageLabelHours(WidgetLayout.Large, model(5 * 60)))
        for (narrow in listOf(WidgetLayout.Tiny, WidgetLayout.Narrow, WidgetLayout.Tall, WidgetLayout.Strip)) {
            assertEquals(null, WidgetLayouts.ageLabelHours(narrow, model(600)))
        }
    }
}
