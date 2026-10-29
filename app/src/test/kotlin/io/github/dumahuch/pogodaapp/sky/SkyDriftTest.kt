package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Дрейф облаков в начале работы. Дима 29.10.2026: «анимация неба начинается не сразу». Причина была в начальной
 * фазе: с нулевой фазой облака стартовали у края хода, где они почти стоят (15 % от наибольшей скорости, а через
 * три секунды вообще разворачиваются), и разгонялись больше десяти секунд.
 */
class SkyDriftTest {

    private val pans = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

    /** Насколько сдвинулось положение облаков за [seconds] секунд после запуска при погоде [weather]. */
    private fun shift(weather: SkyWeather, pan: Double, seconds: Double): Double {
        val speed = SkyDrift.speed(weather)
        return SkyDrift.position(pan, SkyDrift.START_PHASE + speed * seconds) -
            SkyDrift.position(pan, SkyDrift.START_PHASE)
    }

    @Test
    fun cloudsStartAtTheCentreOfTheSwing() {
        for (pan in pans) {
            val centre = SkyDrift.AMPLITUDE + (1 - 2 * SkyDrift.AMPLITUDE) * pan
            assertEquals("pan=$pan", centre, SkyDrift.position(pan, SkyDrift.START_PHASE), 1e-9)
        }
    }

    @Test
    fun cloudsMoveAtFullSpeedFromTheFirstSecond() {
        for (condition in Condition.entries) {
            val weather = SkyWeather.of(condition)
            // Наибольшая скорость покачивания: амплитуда, умноженная на угловую скорость (доля хода в секунду).
            val fastest = SkyDrift.AMPLITUDE * SkyDrift.speed(weather)
            val moved = abs(shift(weather, 0.5, 1.0))
            assertTrue("$condition: за секунду $moved, наибольшая $fastest", moved > 0.99 * fastest)
        }
    }

    @Test
    fun cloudsCoverMostOfFullSpeedDistanceInTheFirstTenSeconds() {
        // Новая фаза даёт от 0,86 (гроза, самый быстрый дрейф) до 0,97 пути на наибольшей скорости.
        // Старая давала от 0,06 до 0,30: сдвиг за десять секунд был почти незаметен.
        for (condition in Condition.entries) {
            val weather = SkyWeather.of(condition)
            val fastest = SkyDrift.AMPLITUDE * SkyDrift.speed(weather)
            val moved = abs(shift(weather, 0.5, 10.0))
            assertTrue("$condition: за десять секунд $moved из возможных ${fastest * 10}", moved > 0.8 * fastest * 10)
        }
    }

    @Test
    fun cloudsKeepMovingInOneDirectionAtFirst() {
        // Не разворачиваются в первые секунды: сдвиг только растёт, пока не пройдена четверть периода.
        for (condition in Condition.entries) {
            val weather = SkyWeather.of(condition)
            var previous = 0.0
            for (second in 1..(weather.driftPeriodSeconds / 4).toInt()) {
                val moved = abs(shift(weather, 0.5, second.toDouble()))
                assertTrue("$condition, секунда $second", moved > previous)
                previous = moved
            }
        }
    }

    @Test
    fun positionStaysInsideTheLayerForEveryPan() {
        for (pan in pans) {
            for (step in 0 until 720) {
                val phase = step * 2 * PI / 720
                val position = SkyDrift.position(pan, phase)
                assertTrue("pan=$pan phase=$phase position=$position", position in -1e-9..1.0 + 1e-9)
            }
        }
    }
}
