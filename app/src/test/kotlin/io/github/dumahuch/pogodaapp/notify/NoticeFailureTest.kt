package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeFailureTest {
    @Test
    fun describesKindMessageAndPlace() {
        val error = SecurityException("Job has a network constraint\nsecond line")
        error.stackTrace = arrayOf(StackTraceElement("android.app.JobSchedulerImpl", "schedule", "J.java", 42))
        assertEquals("SecurityException: Job has a network constraint (JobSchedulerImpl.schedule:42)", NoticeFailure.describe(error))
    }

    @Test
    fun `сбой сети и ответа службы не записывается в строку настроек`() {
        assertTrue(NoticeFailure.isNetwork(ForecastException("No connection to Open-Meteo")))
        assertTrue(NoticeFailure.isNetwork(java.io.IOException("timeout")))
    }

    @Test
    fun `ошибка в коде и в системе записывается`() {
        assertFalse(NoticeFailure.isNetwork(IllegalStateException("bug")))
        assertFalse(NoticeFailure.isNetwork(SecurityException("system")))
    }

    @Test
    fun withoutMessageOnlyTheKind() {
        val error = IllegalStateException()
        error.stackTrace = emptyArray()
        assertEquals("IllegalStateException", NoticeFailure.describe(error))
        assertTrue(NoticeFailure.describe(RuntimeException("x")).startsWith("RuntimeException: x ("))
    }
}
