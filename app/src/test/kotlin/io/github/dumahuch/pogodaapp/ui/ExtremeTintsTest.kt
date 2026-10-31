package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Оттенки максимума и минимума на главном экране: заметны, но яркость остаётся близкой к яркости текста, и без скачков. */
class ExtremeTintsTest {

    private val darkInk = Color(0xFF101722)
    private val lightInk = Color(0xFFF4F7FB)

    private fun rgb(c: Color) = Rgb(c.red * 255.0, c.green * 255.0, c.blue * 255.0)

    @Test
    fun maximumIsWarmAndMinimumIsColdOnBothInks() {
        for ((ink, smoke) in listOf(darkInk to 0f, lightInk to 1f)) {
            for (tint in listOf(ExtremeTints::text, ExtremeTints::arrow)) {
                val warm = tint(ink, smoke, true)
                val cold = tint(ink, smoke, false)
                assertTrue("тёплый: красного заметно больше синего (smoke $smoke)", warm.red - warm.blue >= 0.12f)
                assertTrue("холодный: синего заметно больше красного (smoke $smoke)", cold.blue - cold.red >= 0.12f)
            }
        }
    }

    @Test
    fun arrowKeepsTheTintStrongerThanTheNumber() {
        for ((ink, smoke) in listOf(darkInk to 0f, lightInk to 1f)) {
            val textGap = ExtremeTints.text(ink, smoke, true).let { it.red - it.blue }
            val arrowGap = ExtremeTints.arrow(ink, smoke, true).let { it.red - it.blue }
            assertTrue("стрелка не бледнее числа (smoke $smoke)", arrowGap >= textGap)
        }
    }

    @Test
    fun numberLuminanceStaysCloseToTheInkTheThemeChose() {
        // Тема подбирает яркость текста под небо; оттенок не должен уводить число далеко от неё.
        for ((ink, smoke) in listOf(darkInk to 0f, lightInk to 1f)) {
            val base = luminance(rgb(ink))
            for (warm in listOf(true, false)) {
                val shifted = luminance(rgb(ExtremeTints.text(ink, smoke, warm)))
                assertTrue("сдвиг яркости ${abs(shifted - base)} (smoke $smoke, warm $warm)", abs(shifted - base) <= 0.2)
            }
        }
    }

    @Test
    fun colorChangesSmoothlyWhenTheInkSwitches() {
        // При смене цвета текста (smoke растёт от 0 к 1) оттенок идёт плавно: за шаг 0,01 любой канал меняется меньше чем на 0,02.
        var previous = ExtremeTints.text(darkInk, 0f, true)
        for (step in 1..100) {
            val smoke = step / 100f
            val ink = androidx.compose.ui.graphics.lerp(darkInk, lightInk, smoke)
            val current = ExtremeTints.text(ink, smoke, true)
            assertTrue("скачок красного на шаге $step", abs(current.red - previous.red) < 0.02f)
            assertTrue("скачок зелёного на шаге $step", abs(current.green - previous.green) < 0.02f)
            assertTrue("скачок синего на шаге $step", abs(current.blue - previous.blue) < 0.02f)
            previous = current
        }
    }
}
