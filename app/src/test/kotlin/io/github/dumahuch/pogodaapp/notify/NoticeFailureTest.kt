package io.github.dumahuch.pogodaapp.notify

import org.junit.Assert.assertEquals
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
    fun withoutMessageOnlyTheKind() {
        val error = IllegalStateException()
        error.stackTrace = emptyArray()
        assertEquals("IllegalStateException", NoticeFailure.describe(error))
        assertTrue(NoticeFailure.describe(RuntimeException("x")).startsWith("RuntimeException: x ("))
    }
}
