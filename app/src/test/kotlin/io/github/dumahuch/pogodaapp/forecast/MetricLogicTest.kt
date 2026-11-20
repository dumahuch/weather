package io.github.dumahuch.pogodaapp.forecast

import io.github.dumahuch.pogodaapp.weather.Condition
import io.github.dumahuch.pogodaapp.weather.CurrentWeather
import io.github.dumahuch.pogodaapp.weather.DayRange
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricLogicTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 20)
    private fun at(hour: Int): Instant = day.atStartOfDay(utc).plusHours(hour.toLong()).toInstant()

    private fun details(hour: Int, kmh: Double? = null, direction: Int? = null) = HourDetails(
        time = at(hour), humidity = null, pressureHpa = null, visibilityM = null, windKmh = kmh,
        windDirection = direction, gustKmh = null, uvIndex = null,
    )

    private fun point(hour: Int, mm: Double) = HourPoint(at(hour), 5.0, Condition.Cloudy, true, mm)

    @Test
    fun visibilityFollowsMetOfficeSteps() {
        assertEquals(VisibilityLevel.VeryPoor, VisibilityLevel.ofMeters(999.0))
        assertEquals(VisibilityLevel.Poor, VisibilityLevel.ofMeters(1_000.0))
        assertEquals(VisibilityLevel.Moderate, VisibilityLevel.ofMeters(4_000.0))
        assertEquals(VisibilityLevel.Good, VisibilityLevel.ofMeters(19_999.0))
        assertEquals(VisibilityLevel.VeryGood, VisibilityLevel.ofMeters(24_140.0))
        assertEquals(VisibilityLevel.Excellent, VisibilityLevel.ofMeters(40_000.0))
    }

    @Test
    fun mugginessOnlyFromThirteenDegreesDewPoint() {
        assertNull(Mugginess.ofDewPoint(3.0))
        assertNull(Mugginess.ofDewPoint(12.9))
        assertEquals(Mugginess.Sticky, Mugginess.ofDewPoint(13.0))
        assertEquals(Mugginess.Oppressive, Mugginess.ofDewPoint(18.0))
    }

    @Test
    fun pressureInMillimetresOfMercury() {
        assertEquals(760.0, PressureUnits.shown(1013.25, mmHg = true), 0.05)
        assertEquals(752, PressureUnits.shown(1002.0, mmHg = true).roundToInt())
        assertEquals(1002.0, PressureUnits.shown(1002.0, mmHg = false), 0.0)
    }

    @Test
    fun dominantDirectionWeighsHoursBySpeed() {
        // Слабый северный и сильный западный: побеждает запад.
        val mixed = MetricDays.dominantDirection(listOf(details(0, 2.0, 0), details(1, 20.0, 270), details(2, 20.0, 270)))
        assertEquals(CompassPoint.West, CompassPoint.of(checkNotNull(mixed)))
        assertEquals(273, mixed)
        // Через север: 350° и 10° дают 0°, а не 180°.
        assertEquals(0, MetricDays.dominantDirection(listOf(details(0, 10.0, 350), details(1, 10.0, 10))))
    }

    @Test
    fun noDominantDirectionWhenCalmOrOpposite() {
        assertNull(MetricDays.dominantDirection(emptyList()))
        assertNull(MetricDays.dominantDirection(listOf(details(0, 0.0, 90))))
        assertNull(MetricDays.dominantDirection(listOf(details(0, 10.0, 90), details(1, 10.0, 270))))
    }

    @Test
    fun precipitationSpansJoinNeighbouringHours() {
        val points = listOf(point(0, 0.0), point(1, 0.2), point(2, 0.5), point(3, 0.0), point(4, 0.1))
        assertEquals(
            listOf(PrecipitationSpan(at(1), at(3)), PrecipitationSpan(at(4), at(5))),
            MetricDays.precipitationSpans(points),
        )
        assertEquals(emptyList<PrecipitationSpan>(), MetricDays.precipitationSpans(listOf(point(0, 0.0))))
    }

    @Test
    fun peakIndexIsTheFirstLargestValue() {
        assertEquals(1, MetricDays.peakIndex(listOf(1.0, 3.0, null, 3.0)))
        assertNull(MetricDays.peakIndex(listOf(null, null)))
    }

    @Test
    fun spansShareOneScaleAndKeepAMinimumWidth() {
        val spans = MetricDays.spans(listOf(0.0, 10.0), listOf(20.0, 10.0), floor = 0.0, ceiling = 20.0)
        assertEquals(0f, spans[0].start, 0.0001f)
        assertEquals(1f, spans[0].width, 0.0001f)
        assertEquals(0.5f, spans[1].start, 0.0001f)
        assertEquals(0.06f, spans[1].width, 0.0001f)
    }

    @Test
    fun datesStartTodayAndStopAtSeven() {
        val hours = (0 until 24 * 10).map { HourDetails(at(it), null, null, null, null, null, null, null) }
        val forecast = Forecast(
            zone = utc,
            current = CurrentWeather(at(0), 5.0, 5.0, Condition.Cloudy, true),
            today = DayRange(6.0, 2.0),
            hours = emptyList(),
            hourDetails = hours,
        )
        val dates = MetricDays.dates(forecast, day.plusDays(1))
        assertEquals(7, dates.size)
        assertEquals(day.plusDays(1), dates.first())
        assertEquals(24, MetricDays.detailsOf(hours, utc, day.plusDays(2)).size)
    }
}
