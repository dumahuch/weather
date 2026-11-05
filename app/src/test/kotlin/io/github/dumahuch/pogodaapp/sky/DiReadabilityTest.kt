package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Фон «Ди»: те же требования к читаемости, что у «Нейтрального» ([SkyReadabilityTest]), на всём пути солнца,
 * во всех видах погоды, при разных положениях облаков и подъёме гряд прокруткой.
 */
class DiReadabilityTest {

    private val sampler = SkySampler(DiRaster(FileInputStream(File("src/main/assets/${DiMaps.ASSET_PATH}")).use { DiMaps.read(it) }))

    private class Moment(val name: String, val elevation: Double, val solarHour: Double)

    private val moments = listOf(
        Moment("глубокая ночь", -40.0, 2.0), Moment("ночь перед рассветом", -14.0, 5.0), Moment("предрассветная синева", -9.0, 5.8),
        Moment("рассвет", -3.0, 6.7), Moment("восход", 1.0, 7.0), Moment("раннее утро", 5.0, 8.0), Moment("утро", 12.0, 9.5),
        Moment("полдень", 26.0, 12.0), Moment("день", 20.0, 14.0), Moment("предзакатное", 8.0, 15.7),
        Moment("закат", 0.0, 17.0), Moment("после заката", -5.0, 17.5), Moment("сумерки", -10.0, 18.2),
        Moment("поздние сумерки", -14.0, 18.8), Moment("вечерняя ночь", -30.0, 21.0),
    )
    private val phases = listOf(0.0, 1.6, 3.2, 4.7)
    private val lifts = listOf(-DiArt.MAX_LIFT, 0.0, DiArt.MAX_LIFT)

    private fun palette(m: Moment, condition: Condition, phase: Double, lift: Double): SkyPalette {
        val state = SkyState.of(SunPosition.Sun(m.elevation, m.solarHour), condition)
        val q = SkyTheme.scores(sampler.backdrops(state.look(SkyDrift.position(state.pan, phase), lift)))
        val k = if (q.lightInk.score > q.darkInk.score) 1.0 else 0.0
        return SkyTheme.palette(q, k)
    }

    @Test
    fun textIsReadableEverywhere() {
        val worst = mutableMapOf("цифры" to (Double.MAX_VALUE to ""), "карточки" to (Double.MAX_VALUE to ""),
            "оповещения" to (Double.MAX_VALUE to ""), "кнопки" to (Double.MAX_VALUE to ""), "строки под температурой" to (Double.MAX_VALUE to ""))
        for (m in moments) for (c in Condition.entries) for (phase in phases) for (lift in lifts) {
            val p = palette(m, c, phase, lift)
            val name = "${m.name} / $c / облака $phase / подъём $lift"
            fun track(key: String, value: Double) { if (value < worst.getValue(key).first) worst[key] = value to name }
            track("цифры", p.contrast.hero)
            track("карточки", p.contrast.cards)
            track("оповещения", p.contrast.alerts)
            track("кнопки", p.contrast.buttons)
            track("строки под температурой", p.contrast.info)
        }
        println("«Ди», худшие контрасты: " + worst.entries.joinToString { "${it.key} ${it.value.first} (${it.value.second})" })
        val limits = mapOf("цифры" to 3.0, "карточки" to 4.0, "оповещения" to 4.0, "кнопки" to 4.0, "строки под температурой" to 4.5)
        for ((key, limit) in limits) {
            val (value, where) = worst.getValue(key)
            assertTrue("$key $value ($where)", value >= limit)
        }
    }

    @Test
    fun nightGetsLightTextAndStormIsDarkerThanClearSky() {
        val night = palette(moments.first { it.name == "глубокая ночь" }, Condition.Clear, 0.0, 0.0)
        assertTrue("ночью текст светлый", !night.darkInk)
        val noon = moments.first { it.name == "полдень" }
        val clear = sampler.backdrops(SkyState.of(SunPosition.Sun(noon.elevation, noon.solarHour), Condition.Clear).look(0.5)).card.mean
        val storm = sampler.backdrops(SkyState.of(SunPosition.Sun(noon.elevation, noon.solarHour), Condition.Storm).look(0.5)).card.mean
        assertTrue("гроза темнее ясного неба", luminance(storm) < luminance(clear))
    }

    @Test
    fun liftChangesWhatLiesUnderTheCards() {
        val state = SkyState.of(SunPosition.Sun(26.0, 12.0), Condition.Clear)
        val down = sampler.backdrops(state.look(0.5, -DiArt.MAX_LIFT)).card.mean
        val up = sampler.backdrops(state.look(0.5, DiArt.MAX_LIFT)).card.mean
        assertTrue("подъём гряд меняет фон под карточкой", luminance(down) != luminance(up))
    }
}
