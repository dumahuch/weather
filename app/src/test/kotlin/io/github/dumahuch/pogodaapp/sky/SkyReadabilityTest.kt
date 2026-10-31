package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * То, что в макете проверялось в браузере: на любом времени суток, любой погоде и любом положении
 * облаков текст читаем. Здесь то же на настоящих картах яркости приложения.
 */
class SkyReadabilityTest {

    private val sampler = SkySampler(SkyTestData.maps)

    private class Moment(val name: String, val elevation: Double, val solarHour: Double)

    // Моменты на всём пути солнца: ночь, рассвет, утро, день, закат, сумерки (в разных стадиях).
    private val moments = listOf(
        Moment("глубокая ночь", -40.0, 2.0), Moment("ночь перед рассветом", -14.0, 5.0), Moment("предрассветная синева", -9.0, 5.8),
        Moment("рассвет", -3.0, 6.7), Moment("восход", 1.0, 7.0), Moment("раннее утро", 5.0, 8.0), Moment("утро", 12.0, 9.5),
        Moment("полдень", 26.0, 12.0), Moment("день", 20.0, 14.0), Moment("предзакатное", 8.0, 15.7),
        Moment("закат", 0.0, 17.0), Moment("после заката", -5.0, 17.5), Moment("сумерки", -10.0, 18.2),
        Moment("поздние сумерки", -14.0, 18.8), Moment("вечерняя ночь", -30.0, 21.0),
    )

    /** Фазы покачивания облаков: от крайнего левого до крайнего правого положения. */
    private val phases = listOf(0.0, 1.6, 3.2, 4.7)

    private fun palette(m: Moment, condition: Condition, phase: Double): SkyPalette {
        val state = SkyState.of(SunPosition.Sun(m.elevation, m.solarHour), condition)
        val q = SkyTheme.scores(sampler.backdrops(state.look(SkyDrift.position(state.pan, phase))))
        val k = if (q.lightInk.score > q.darkInk.score) 1.0 else 0.0
        return SkyTheme.palette(q, k)
    }

    @Test
    fun textIsReadableEverywhere() {
        var worstCards = Double.MAX_VALUE
        var worstHero = Double.MAX_VALUE
        var worstAlerts = Double.MAX_VALUE
        var worstButtons = Double.MAX_VALUE
        var worstInfo = Double.MAX_VALUE
        var infoWhere = ""
        var where = ""
        for (m in moments) for (c in Condition.entries) for (phase in phases) {
            val p = palette(m, c, phase)
            val name = "${m.name} / $c / фаза облаков $phase"
            if (p.contrast.cards < worstCards) { worstCards = p.contrast.cards; where = "карточки: $name" }
            worstHero = minOf(worstHero, p.contrast.hero)
            worstAlerts = minOf(worstAlerts, p.contrast.alerts)
            worstButtons = minOf(worstButtons, p.contrast.buttons)
            if (p.contrast.info < worstInfo) { worstInfo = p.contrast.info; infoWhere = name }
        }
        println("Худшие контрасты: цифры $worstHero, карточки $worstCards ($where), оповещения $worstAlerts, кнопки $worstButtons, строки под температурой $worstInfo ($infoWhere)")
        assertTrue("цифры $worstHero", worstHero >= 3.0)
        assertTrue("карточки $worstCards ($where)", worstCards >= 4.0)
        assertTrue("оповещения $worstAlerts", worstAlerts >= 4.0)
        assertTrue("кнопки $worstButtons", worstButtons >= 4.0)
        // Строки под температурой лежат на небе без стекла: основной цвет текста должен держать 4,5 : 1 (WCAG AA) за счёт подложки.
        assertTrue("строки под температурой $worstInfo ($infoWhere)", worstInfo >= 4.5)
    }

    @Test
    fun infoScrimIsNeededOnlyWhereTheSkyDoesNotGiveReadableTextAndStaysWithinTheCap() {
        var withScrim = 0
        var total = 0
        var densest = 0.0
        for (m in moments) for (c in Condition.entries) for (phase in phases) {
            val p = palette(m, c, phase)
            total++
            if (p.infoScrim.alpha > 0.0) withScrim++
            densest = maxOf(densest, p.infoScrim.alpha)
            // Там, где подложки нет, небо само держит контраст.
            if (p.infoScrim.alpha == 0.0) {
                assertTrue("без подложки контраст ${p.contrast.info} (${m.name} / $c)", p.contrast.info >= SkyTheme.INFO_TARGET)
            }
        }
        println("Подложка под строками под температурой: в $withScrim из $total состояний, самая плотная $densest")
        assertTrue("потолок подложки: $densest", densest <= SkyTheme.INFO_SCRIM_MAX + 1e-9)
        // С версии 0.5.4 цель выше (5,5) и подписи без тени букв, подложка нужна чаще (до 0.5.3 было меньше 60 %).
        assertTrue("подложка нужна не везде, а только на части небес ($withScrim из $total)", withScrim < total * 0.75)
    }

    @Test
    fun nightGetsLightTextAndClearNoonGetsDarkText() {
        val night = palette(moments.first { it.name == "глубокая ночь" }, Condition.Clear, 0.0)
        val noon = palette(moments.first { it.name == "полдень" }, Condition.Clear, 0.0)
        assertTrue("ночью текст светлый", !night.darkInk)
        assertTrue("в ясный полдень текст тёмный", noon.darkInk)
    }

    @Test
    fun stormMakesTheBackdropDarkerThanClearSky() {
        val m = moments.first { it.name == "полдень" }
        val clear = SkyState.of(SunPosition.Sun(m.elevation, m.solarHour), Condition.Clear)
        val storm = SkyState.of(SunPosition.Sun(m.elevation, m.solarHour), Condition.Storm)
        val a = sampler.backdrops(clear.look(0.5)).card.mean
        val b = sampler.backdrops(storm.look(0.5)).card.mean
        assertTrue("гроза должна быть темнее: ${luminance(a)} против ${luminance(b)}", luminance(b) < luminance(a))
    }

    @Test
    fun switchHasHysteresisAndMovesSmoothly() {
        val daySky = SkyTheme.scores(sampler.backdrops(SkyState.of(SunPosition.Sun(26.0, 12.0), Condition.Clear).look(0.5)))
        val nightSky = SkyTheme.scores(sampler.backdrops(SkyState.of(SunPosition.Sun(-40.0, 2.0), Condition.Clear).look(0.5)))
        val ink = InkSwitch()
        assertEquals(0.0, ink.update(daySky, 0.0), 1e-9)
        // Ночью переключатель идёт к светлому не мгновенно: за 0,1 с проходит треть пути.
        val first = ink.update(nightSky, 0.1)
        assertTrue("шаг $first", first > 0.0 && first < 1.0)
        assertEquals(1.0, ink.targetPosition, 1e-9)
        var position = first
        repeat(10) { position = ink.update(nightSky, 0.1) }
        assertEquals(1.0, position, 1e-9)
        // Возврат к тёмному только когда тёмный вариант заметно лучше.
        assertEquals(1.0, ink.update(nightSky, 0.1), 1e-9)
    }
}
