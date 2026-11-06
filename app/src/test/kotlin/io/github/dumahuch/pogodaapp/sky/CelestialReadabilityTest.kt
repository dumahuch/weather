package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Текст остаётся читаемым, когда на небе солнце или луна (любая фаза и высота), у обоих фонов:
 * диск ложится под цифры, карточки и строки под температурой, подложка и цвет текста подбираются по нему.
 */
class CelestialReadabilityTest {

    private class Background(val name: String, val sampler: SkySampler, val info: Double)

    private val backgrounds = listOf(
        Background("«Ди»", SkySampler(DiRaster(FileInputStream(File("src/main/assets/${DiMaps.ASSET_PATH}")).use { DiMaps.read(it) })), 3.4),
        Background("«Нейтральный»", SkySampler(File("src/main/assets/${SkyMaps.ASSET_PATH}").inputStream().use { SkyMaps.read(it) }), 4.5),
    )

    private class Moment(val name: String, val elevation: Double, val solarHour: Double)

    private val moments = listOf(
        Moment("глубокая ночь", -40.0, 2.0), Moment("рассвет", -3.0, 6.7), Moment("утро", 12.0, 9.5),
        Moment("полдень", 26.0, 12.0), Moment("закат", 0.0, 17.0), Moment("сумерки", -8.0, 18.0),
        Moment("вечерняя ночь", -30.0, 21.0),
    )

    /** Луна: разные высоты и стороны, все четыре освещённости (картинки меняются по фазе). */
    private val moons = listOf(
        null,
        MoonSky(Moon.Position(15.0, -4.0), Moon.Phase(1.0, 0.5), false),
        MoonSky(Moon.Position(35.0, 0.0), Moon.Phase(0.75, 0.4), false),
        MoonSky(Moon.Position(50.0, 2.0), Moon.Phase(0.5, 0.25), false),
        MoonSky(Moon.Position(25.0, 5.0), Moon.Phase(0.25, 0.9), false),
    )

    private val conditions = listOf(Condition.Clear, Condition.Partly, Condition.Cloudy, Condition.Rain)

    @Test
    fun textIsReadableWithTheSunAndTheMoonOnTheSky() {
        for (background in backgrounds) {
            val worst = mutableMapOf("цифры" to Double.MAX_VALUE, "карточки" to Double.MAX_VALUE, "оповещения" to Double.MAX_VALUE,
                "кнопки" to Double.MAX_VALUE, "строки" to Double.MAX_VALUE)
            val where = mutableMapOf<String, String>()
            for (m in moments) for (moon in moons) for (c in conditions) for (phase in listOf(0.0, 3.2)) {
                val state = SkyState.of(SunPosition.Sun(m.elevation, m.solarHour), c, null, null, moon)
                val q = SkyTheme.scores(background.sampler.backdrops(state.look(SkyDrift.position(state.pan, phase))))
                val p = SkyTheme.palette(q, if (q.lightInk.score > q.darkInk.score) 1.0 else 0.0)
                val name = "${m.name} / $c / луна ${moon?.phase?.illumination} на ${moon?.position?.elevation}° / облака $phase"
                fun track(key: String, value: Double) { if (value < worst.getValue(key)) { worst[key] = value; where[key] = name } }
                track("цифры", p.contrast.hero); track("карточки", p.contrast.cards); track("оповещения", p.contrast.alerts)
                track("кнопки", p.contrast.buttons); track("строки", p.contrast.info)
            }
            println("${background.name} с диском, худшие контрасты: " + worst.entries.joinToString { "${it.key} ${it.value} (${where[it.key]})" })
            val limits = mapOf("цифры" to 3.0, "карточки" to 4.0, "оповещения" to 4.0, "кнопки" to 4.0, "строки" to background.info)
            val failed = limits.filter { (key, limit) -> worst.getValue(key) < limit }.map { (key, limit) -> "${background.name}: $key ${worst.getValue(key)} < $limit (${where[key]})" }
            assertTrue(failed.joinToString("; "), failed.isEmpty())
        }
    }
}
