package io.github.dumahuch.pogodaapp

import org.junit.Assert.assertEquals
import org.junit.Test

class JobIdsTest {
    @Test
    fun `номера фоновых задач не повторяются`() {
        assertEquals(JobIds.ALL.size, JobIds.ALL.toSet().size)
    }

    @Test
    fun `в списке все номера из объекта`() {
        val declared = JobIds::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.getInt(null) }
        assertEquals(declared.toSet(), JobIds.ALL.toSet())
    }
}
