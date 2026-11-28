package io.github.dumahuch.pogodaapp.ui.map

import io.github.dumahuch.pogodaapp.map.MapDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MapStatusTest {
    private val now = Instant.parse("2026-11-27T12:00:00Z")

    private fun state(
        layer: MapLayer = MapLayer.CloudsAndRain,
        rain: FrameStatus = FrameStatus.Ready,
        clouds: FrameStatus = FrameStatus.Ready,
        field: FrameStatus = FrameStatus.Ready,
        done: Int = 0,
        total: Int = 0,
    ) = MapUiState(now, MapDay.Today, now, layer, playing = false, rain = rain, clouds = clouds, field = field, framesDone = done, framesTotal = total)

    @Test
    fun `всё загружено, сказать нечего`() {
        assertNull(mapStatus(state(), baseFailed = false))
    }

    @Test
    fun `не ответил только радар при готовых облаках`() {
        val status = mapStatus(state(rain = FrameStatus.Failed), baseFailed = false)
        assertEquals(MapStatus.RadarFailed, status)
        assertTrue(status!!.retryable)
    }

    @Test
    fun `не ответили только облака`() {
        assertEquals(MapStatus.CloudsFailed, mapStatus(state(clouds = FrameStatus.Failed), baseFailed = false))
    }

    @Test
    fun `не ответило всё, что нужно слою, это нет связи`() {
        val both = state(rain = FrameStatus.Failed, clouds = FrameStatus.Failed)
        assertEquals(MapStatus.NoConnection, mapStatus(both, baseFailed = false))
    }

    @Test
    fun `у слоя только с дождём сбой радара это нет связи`() {
        val rainOnly = state(layer = MapLayer.RainOnly, rain = FrameStatus.Failed, clouds = FrameStatus.Failed)
        assertEquals(MapStatus.NoConnection, mapStatus(rainOnly, baseFailed = false))
    }

    @Test
    fun `температура и ветер с отказом поля`() {
        val temperature = state(layer = MapLayer.Temperature, field = FrameStatus.Failed)
        assertEquals(MapStatus.NoConnection, mapStatus(temperature, baseFailed = false))
    }

    @Test
    fun `основа карты не загрузилась важнее всего и не повторяется касанием`() {
        val status = mapStatus(state(rain = FrameStatus.Failed), baseFailed = true)
        assertEquals(MapStatus.BaseFailed, status)
        assertFalse(status!!.retryable)
    }

    @Test
    fun `нет данных и счётчик кадров`() {
        assertEquals(MapStatus.NoClouds, mapStatus(state(clouds = FrameStatus.Missing), baseFailed = false))
        assertEquals(MapStatus.Loading, mapStatus(state(done = 3, total = 8), baseFailed = false))
    }
}
