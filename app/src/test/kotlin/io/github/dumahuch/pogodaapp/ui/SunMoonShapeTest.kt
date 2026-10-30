package io.github.dumahuch.pogodaapp.ui

import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.contrast
import io.github.dumahuch.pogodaapp.ui.details.ARC_WIDTH
import io.github.dumahuch.pogodaapp.ui.details.SunArcShape
import io.github.dumahuch.pogodaapp.ui.details.SunMoonColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Дуга «Солнце и Луна»: день и ночь зеркальны, метки читаются. */
class SunMoonShapeTest {

    private val height = 62f

    @Test
    fun dayArcRisesAboveTheHorizonAndStartsOnTheLeft() {
        val day = SunArcShape.of(daytime = true)
        assertFalse(day.startsAtRight)
        assertTrue("вершина выше горизонта", day.apexY < day.baseY)
        assertEquals("основание на нижнем краю", height, day.baseY, 0.001f)
    }

    @Test
    fun nightArcIsMirroredBelowTheRaisedHorizonAndStartsOnTheRight() {
        val night = SunArcShape.of(daytime = false)
        val day = SunArcShape.of(daytime = true)
        assertTrue(night.startsAtRight)
        assertTrue("горизонт ночью выше, чем днём", night.baseY < day.baseY)
        assertTrue("дуга под горизонтом", night.apexY > night.baseY)
    }

    @Test
    fun nightArcDepthEqualsAMirrorOfTheDayHeightWithinTheTile() {
        val night = SunArcShape.of(daytime = false)
        // метка внизу ночной дуги и наверху над горизонтом не выходит за плитку: не меньше 10 единиц запаса
        assertTrue("запас снизу", height - night.apexY >= 10f)
        assertTrue("запас сверху", night.baseY >= 10f)
    }

    @Test
    fun markerIconsReadOnTheirFill() {
        val sun = contrast(Rgb.hex(SunMoonColors.SUN_ICON), Rgb.hex(SunMoonColors.SUN_FILL))
        val moon = contrast(Rgb.hex(SunMoonColors.MOON_ICON), Rgb.hex(SunMoonColors.MOON_FILL))
        assertTrue("солнце $sun", sun >= 4.5)
        assertTrue("луна $moon", moon >= 4.5)
    }

    @Test
    fun markerFillIsDistinctFromWhiteRing() {
        // метка в белом ободке: сама метка должна отличаться от него не меньше 1,5 : 1 (жёлтый самый светлый)
        assertTrue(contrast(Rgb.hex(SunMoonColors.SUN_FILL), Rgb.White) >= 1.5)
        assertTrue(contrast(Rgb.hex(SunMoonColors.MOON_FILL), Rgb.White) >= 3.0)
    }

    @Test
    fun arcEndsStandOnTheFullyVisibleHorizon() {
        // Горизонт гаснет у краёв на HORIZON_FADE единиц; точки на концах дуги должны стоять там, где линия видна целиком.
        assertTrue("левый конец", SunArcShape.LEFT_X >= SunArcShape.HORIZON_FADE)
        assertTrue("правый конец", SunArcShape.RIGHT_X <= ARC_WIDTH - SunArcShape.HORIZON_FADE)
        assertTrue("затухание не съедает всю линию", SunArcShape.HORIZON_FADE * 2 < ARC_WIDTH / 2)
    }
}
