package io.github.dumahuch.pogodaapp.home

import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroGlyphPlacementTest {

    private val strokes = listOf(1.2f, 1.6f)

    @Test
    fun partsHangingBelowTheCloudNeverGoAboveItsBottomEdge() {
        for (style in WeatherIconStyle.values()) for (icon in HeroIcon.values()) for (stroke in strokes) {
            assertTrue(
                "$style $icon: рисунок не может заканчиваться выше облака",
                glyphBottom(icon, style, stroke) >= glyphCloudBottom(icon, style),
            )
        }
    }

    @Test
    fun aCloudAloneHasNothingHangingBelowIt() {
        assertEquals(glyphCloudBottom(HeroIcon.Cloud, WeatherIconStyle.Glass), glyphBottom(HeroIcon.Cloud, WeatherIconStyle.Glass, 1.2f), 0f)
    }

    @Test
    fun rainBoltAndSnowHangLowerThanThePlainCloud() {
        for (style in WeatherIconStyle.values()) {
            val plain = glyphBottom(HeroIcon.Cloud, style, 1.2f) - glyphCloudBottom(HeroIcon.Cloud, style)
            for (icon in listOf(HeroIcon.Rain, HeroIcon.Storm, HeroIcon.Snow)) {
                val hang = glyphBottom(icon, style, 1.2f) - glyphCloudBottom(icon, style)
                assertTrue("$style $icon: свисающие части ниже облака", hang > plain + 3f)
            }
        }
    }

    @Test
    fun fogHasNoCloudAndNothingHangsBelowItsLowestLine() {
        // Туман без облака (step-41): нижняя полоса стоит там же, где у остальных нижний край облака, и ничего не свисает.
        for (style in WeatherIconStyle.values()) {
            assertEquals(
                "$style: у тумана нет частей ниже нижней полосы",
                glyphCloudBottom(HeroIcon.Fog, style),
                glyphBottom(HeroIcon.Fog, style, 0f),
                0f,
            )
            assertEquals(
                "$style: нижняя полоса на уровне нижнего края облака",
                glyphCloudBottom(HeroIcon.Cloud, style),
                glyphCloudBottom(HeroIcon.Fog, style),
                0.6f,
            )
        }
    }

    @Test
    fun hangingPartsDoNotSinkFarBelowTheDigitsBaseline() {
        // Под цифрами стоит надпись с погодой: у значка есть запас порядка 3 единиц (около 10 dp при значке 80 dp).
        for (style in WeatherIconStyle.values()) for (icon in HeroIcon.values()) {
            val belowBaseline = glyphBottom(icon, style, 1.2f) - glyphCloudBottom(icon, style) - glyphLift(style)
            assertTrue("$style $icon: свисает на $belowBaseline ниже базовой линии", belowBaseline <= 3.5f)
        }
    }

    @Test
    fun theLiftKeepsTheCloudSunBelowTheDegreeSign() {
        // Знак градуса кончается примерно на 22 единицах значка над нижним краем облака (измерено у шрифта Manrope при
        // значке 80 dp и цифрах 124 sp); у облака с солнцем наклонные лучи доходят до 15,8 единицы от нижнего края облака
        // у стеклянного набора и до 17 у контурного. Нужен просвет не меньше 1 единицы.
        val degreeBottomAboveBaseline = 22.2f
        assertTrue(glyphLift(WeatherIconStyle.Glass) + 15.8f + 1f <= degreeBottomAboveBaseline)
        assertTrue(glyphLift(WeatherIconStyle.Outline) + 17f + 1f <= degreeBottomAboveBaseline)
    }
}
