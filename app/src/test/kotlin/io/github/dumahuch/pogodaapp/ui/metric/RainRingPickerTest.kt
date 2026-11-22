package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Час на круге осадков по углу от центра: сверху полночь, дальше по часовой, час = 15 градусов. */
class RainRingPickerTest {

    private val center = Offset(100f, 100f)

    private fun hour(dx: Float, dy: Float) = hourAt(Offset(center.x + dx, center.y + dy), center)

    @Test
    fun `сверху полночь, справа шесть, снизу двенадцать, слева восемнадцать`() {
        assertEquals(0, hour(1f, -50f))
        assertEquals(6, hour(50f, 1f))
        assertEquals(12, hour(-1f, 50f))
        assertEquals(18, hour(-50f, -1f))
    }

    @Test
    fun `слева от верха последний час суток`() {
        assertEquals(23, hour(-1f, -50f))
    }

    @Test
    fun `расстояние от центра не важно, палец может быть далеко от круга`() {
        assertEquals(3, hour(70f, -70f))
        assertEquals(3, hour(300f, -300f))
        assertEquals(21, hour(-300f, -300f))
    }
}
