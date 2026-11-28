package io.github.dumahuch.pogodaapp

import org.junit.Assert.assertEquals
import org.junit.Test

class JobIdsTest {
    @Test
    fun `номера фоновых задач не повторяются`() {
        assertEquals(JobIds.ALL.size, JobIds.ALL.toSet().size)
    }

    @Test
    fun `заняты все восемь номеров`() {
        assertEquals(8, JobIds.ALL.size)
        assertEquals(JobIds.MAP_CLEANUP, JobIds.ALL.last())
    }
}
