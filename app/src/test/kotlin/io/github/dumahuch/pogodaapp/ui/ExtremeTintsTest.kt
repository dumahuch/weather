package io.github.dumahuch.pogodaapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Оттенки максимума и минимума на главном экране: заметны там, где небо даёт запас контраста, никогда не лишают число
 * читаемости и подстраиваются под худший цвет неба под строкой.
 */
class ExtremeTintsTest {

    private val darkInk = Color(0xFF101722)
    private val lightInk = Color(0xFFF4F7FB)

    /** Небо с большим запасом контраста для каждого текста: светлое под тёмным и тёмное под светлым. */
    private val lightSky = Color(0xFFE6EEF6)
    private val darkSky = Color(0xFF141C2B)

    private val easy = listOf(Triple(darkInk, 0f, lightSky), Triple(lightInk, 1f, darkSky))

    private fun rgb(c: Color) = Rgb(c.red * 255.0, c.green * 255.0, c.blue * 255.0)

    @Test
    fun maximumIsWarmAndMinimumIsColdWhenTheSkyAllowsIt() {
        for ((ink, smoke, sky) in easy) {
            for (tint in listOf(ExtremeTints::text, ExtremeTints::arrow)) {
                val warm = tint(ink, smoke, true, sky)
                val cold = tint(ink, smoke, false, sky)
                assertTrue("тёплый: красного заметно больше синего (smoke $smoke)", warm.red - warm.blue >= 0.12f)
                assertTrue("холодный: синего заметно больше красного (smoke $smoke)", cold.blue - cold.red >= 0.12f)
            }
        }
    }

    @Test
    fun arrowKeepsTheTintStrongerThanTheNumber() {
        for ((ink, smoke, sky) in easy) {
            val textGap = ExtremeTints.text(ink, smoke, true, sky).let { it.red - it.blue }
            val arrowGap = ExtremeTints.arrow(ink, smoke, true, sky).let { it.red - it.blue }
            assertTrue("стрелка не бледнее числа (smoke $smoke)", arrowGap >= textGap)
        }
    }

    @Test
    fun numberLuminanceStaysCloseToTheInkTheThemeChose() {
        // Тема подбирает яркость текста под небо; оттенок не должен уводить число далеко от неё.
        for ((ink, smoke, sky) in easy) {
            val base = luminance(rgb(ink))
            for (warm in listOf(true, false)) {
                val shifted = luminance(rgb(ExtremeTints.text(ink, smoke, warm, sky)))
                assertTrue("сдвиг яркости ${abs(shifted - base)} (smoke $smoke, warm $warm)", abs(shifted - base) <= 0.2)
            }
        }
    }

    @Test
    fun numberAndArrowKeepTheirContrastOnAnySkyWhereTheInkItselfIsReadable() {
        // Перебор неба от чёрного до белого: если основной цвет текста держит нужный контраст, число и стрелка тоже.
        for ((ink, smoke) in listOf(darkInk to 0f, lightInk to 1f)) {
            for (step in 0..100) {
                val sky = lerp(Color.Black, Color.White, step / 100f)
                for (warm in listOf(true, false)) {
                    val text = ExtremeTints.text(ink, smoke, warm, sky)
                    val arrow = ExtremeTints.arrow(ink, smoke, warm, sky)
                    if (ExtremeTints.contrastRatio(ink, sky) >= ExtremeTints.MIN_TEXT_CONTRAST) {
                        assertTrue(
                            "число ${ExtremeTints.contrastRatio(text, sky)} (smoke $smoke, небо $step %, warm $warm)",
                            ExtremeTints.contrastRatio(text, sky) >= ExtremeTints.MIN_TEXT_CONTRAST,
                        )
                    } else {
                        // Основной цвет сам не читается: число остаётся основным, тема отвечает за подложку.
                        assertEquals(ink, text)
                    }
                    if (ExtremeTints.contrastRatio(ink, sky) >= ExtremeTints.MIN_ARROW_CONTRAST) {
                        assertTrue(
                            "стрелка ${ExtremeTints.contrastRatio(arrow, sky)} (smoke $smoke, небо $step %, warm $warm)",
                            ExtremeTints.contrastRatio(arrow, sky) >= ExtremeTints.MIN_ARROW_CONTRAST,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun tintFadesAsTheSkyApproachesTheInk() {
        // Чем ближе небо к цвету текста, тем слабее оттенок: сила никогда не растёт при уменьшении запаса контраста.
        for ((ink, smoke, far) in easy) {
            var previous = Float.MAX_VALUE
            for (step in 0..200) {
                val sky = lerp(far, ink, step / 200f)
                val gap = ExtremeTints.text(ink, smoke, true, sky).let { it.red - it.blue }
                val coldGap = ExtremeTints.text(ink, smoke, false, sky).let { it.blue - it.red }
                val strength = minOf(gap, coldGap)
                assertTrue("оттенок вырос на шаге $step (smoke $smoke): $strength > $previous", strength <= previous + 1e-4f)
                previous = strength
            }
        }
    }

    @Test
    fun colorChangesSmoothlyWhenTheInkSwitches() {
        // При смене цвета текста (smoke растёт от 0 к 1) на небе с запасом оттенок идёт плавно:
        // за шаг 0,01 любой канал меняется меньше чем на 0,03. Небо плавно переходит от светлого к тёмному вместе с текстом;
        // середина (серый текст на сером небе) не проверяется: там читаемого цвета нет ни при каком оттенке.
        var previous = ExtremeTints.text(darkInk, 0f, true, lightSky)
        for (step in 1..100) {
            val smoke = step / 100f
            val ink = lerp(darkInk, lightInk, smoke)
            val backdrop = lerp(lightSky, darkSky, smoke)
            val current = ExtremeTints.text(ink, smoke, true, backdrop)
            if (smoke < 0.2f || smoke > 0.8f) {
                assertTrue("скачок красного на шаге $step", abs(current.red - previous.red) < 0.03f)
                assertTrue("скачок зелёного на шаге $step", abs(current.green - previous.green) < 0.03f)
                assertTrue("скачок синего на шаге $step", abs(current.blue - previous.blue) < 0.03f)
            }
            previous = current
        }
    }
}
