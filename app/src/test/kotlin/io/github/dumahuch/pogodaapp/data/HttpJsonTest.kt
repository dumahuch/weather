package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpJsonTest {

    private val farDeadline get() = System.nanoTime() + 60_000_000_000L

    @Test
    fun readsASmallAnswerWhole() {
        val bytes = """{"ok":true,"город":"Ганновер"}""".toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, HttpJson.readBounded(ByteArrayInputStream(bytes), 1024, farDeadline))
    }

    @Test
    fun anAnswerExactlyAtTheLimitIsAccepted() {
        val bytes = ByteArray(20_000) { 'a'.code.toByte() }
        assertEquals(20_000, HttpJson.readBounded(ByteArrayInputStream(bytes), 20_000, farDeadline).size)
    }

    @Test
    fun anAnswerOverTheLimitIsRefusedNotBuffered() {
        val bytes = ByteArray(20_001) { 'a'.code.toByte() }
        assertThrows(ForecastException::class.java) {
            HttpJson.readBounded(ByteArrayInputStream(bytes), 20_000, farDeadline)
        }
    }

    @Test
    fun anAnswerThatTakesTooLongIsRefused() {
        val pastDeadline = System.nanoTime() - 1
        assertThrows(ForecastException::class.java) {
            HttpJson.readBounded(ByteArrayInputStream(ByteArray(10)), 1024, pastDeadline)
        }
    }

    @Test
    fun anUnprotectedAddressIsRefusedBeforeAnyConnection() {
        assertThrows(ForecastException::class.java) { HttpJson.get("http://api.open-meteo.com/v1/forecast", "test") }
        assertThrows(ForecastException::class.java) { HttpJson.get("ftp://example.org/x", "test") }
    }

    @Test
    fun theLimitFitsARealFifteenDayAnswer() {
        val sample = javaClass.classLoader!!.getResourceAsStream("open-meteo/hannover-15d.json")!!.readBytes()
        assertTrue(sample.size < HttpJson.MAX_BODY_BYTES / 4)
    }
}
