package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.sky.SkyTestData.distance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Сверка с макетом, который одобрил Дима. Числа (golden.json) сняты с настоящей страницы макета
 * в браузере: для девяти моментов демо-дня, семи видов погоды и двух положений облаков.
 */
class SkyGoldenTest {

    private val golden = SkyTestData.golden()

    @Test
    fun goldenCoversTheWholeGrid() {
        assertEquals(9 * 7 * 2, golden.size)
    }

    @Test
    fun weightsMatchTheMockup() {
        for (g in golden) {
            val w = SkyTestData.weightsOf(g)
            for (scene in SkyScene.entries) {
                assertEquals("${g.label} ${scene.fileName}", g.weight(scene), w[scene], 1e-9)
            }
        }
    }

    @Test
    fun skyColoursByFormulaMatchTheMockup() {
        for (g in golden) {
            val v = SkyColors.veil(g.elevation, SkyTimeOfDay.evening(g.hour), g.weather)
            val want = g.veil()
            assertTrue("${g.label} верх", distance(v.top, want.top) < 0.01)
            assertTrue("${g.label} середина", distance(v.mid, want.mid) < 0.01)
            assertTrue("${g.label} горизонт", distance(v.hor, want.hor) < 0.01)
        }
    }

    @Test
    fun themeMathMatchesTheMockupForTheSameBackdrops() {
        for (g in golden) {
            val q = SkyTheme.scores(g.backdrops())
            for ((name, side) in listOf("D" to q.darkInk, "L" to q.lightInk)) {
                val want = g.side(name)
                val label = "${g.label} $name"
                assertEquals("$label вуаль", want.getDouble("sc"), side.scrim, 1e-6)
                assertEquals("$label цифры", want.getDouble("hero"), side.hero, 1e-6)
                assertEquals("$label стекло", want.getDouble("a"), side.glassOpacity, 1e-6)
                assertEquals("$label кнопки", want.getDouble("aB"), side.buttonOpacity, 1e-6)
                assertEquals("$label нужно карточкам", want.getDouble("aNc"), side.needCards, 1e-6)
                assertEquals("$label нужно оповещениям", want.getDouble("aNa"), side.needAlerts, 1e-6)
                assertEquals("$label нужно кнопкам", want.getDouble("aNb"), side.needButtons, 1e-6)
                assertEquals("$label оценка", want.getDouble("score"), side.score, 1e-6)
            }
        }
    }

    @Test
    fun paletteMatchesTheMockupAtBothInks() {
        for (g in golden) {
            val q = SkyTheme.scores(g.backdrops())
            for ((name, k) in listOf("k0" to 0.0, "k1" to 1.0, "kh" to 0.5)) {
                val want = g.theme(name)
                val p = SkyTheme.palette(q, k)
                val label = "${g.label} $name"
                assertEquals("$label карточки", want.getDouble("a"), p.glass.alpha, 1e-6)
                assertEquals("$label панель", want.getDouble("aB"), p.buttonBackground.alpha, 1e-6)
                assertEquals("$label оповещения", want.getDouble("aAl"), p.glassAlert.alpha, 1e-6)
                assertEquals("$label вуаль", want.getDouble("sc"), p.scrim.alpha, 1e-6)
                assertEquals("$label контраст карточек", want.getDouble("ccCards"), p.contrast.cards, 1e-6)
                assertEquals("$label контраст оповещений", want.getDouble("ccAlert"), p.contrast.alerts, 1e-6)
                assertEquals("$label контраст кнопок", want.getDouble("ccBtn"), p.contrast.buttons, 1e-6)
            }
        }
    }

    @Test
    fun backdropsFromTheMapsAreCloseToWhatTheMockupSaw() {
        // Картины в макете сжаты иначе, чем карты приложения, поэтому допуск в несколько единиц цвета.
        val sampler = SkySampler(SkyTestData.maps)
        var worstMean = 0.0
        var worstPatch = 0.0
        var worstLabel = ""
        for (g in golden) {
            val got = sampler.backdrops(SkyTestData.look(g))
            val want = g.backdrops()
            val pairs = listOf(
                "top" to (got.top to want.top), "mid" to (got.mid to want.mid), "hor" to (got.hor to want.hor),
                "hero" to (got.hero.mean to want.hero.mean), "alert" to (got.alert.mean to want.alert.mean),
                "card" to (got.card.mean to want.card.mean), "dock" to (got.dock.mean to want.dock.mean),
            )
            for ((name, pair) in pairs) {
                val d = distance(pair.first, pair.second)
                if (d > worstMean) { worstMean = d; worstLabel = "${g.label} $name" }
            }
            val patches = listOf(
                got.hero.underDarkInk to want.hero.underDarkInk, got.hero.underLightInk to want.hero.underLightInk,
                got.alert.underDarkInk to want.alert.underDarkInk, got.alert.underLightInk to want.alert.underLightInk,
                got.card.underDarkInk to want.card.underDarkInk, got.card.underLightInk to want.card.underLightInk,
                got.dock.underDarkInk to want.dock.underDarkInk, got.dock.underLightInk to want.dock.underLightInk,
            )
            for ((a, b) in patches) worstPatch = maxOf(worstPatch, distance(a, b))
        }
        println("Наибольшее расхождение средних цветов: $worstMean ($worstLabel), пятен: $worstPatch")
        assertTrue("средние цвета расходятся на $worstMean ($worstLabel)", worstMean <= 8.0)
        assertTrue("пятна расходятся на $worstPatch", worstPatch <= 14.0)
    }

    @Test
    fun readabilityDecisionsAreCloseToTheMockupToo() {
        val sampler = SkySampler(SkyTestData.maps)
        var worst = 0.0
        for (g in golden) {
            val q = SkyTheme.scores(sampler.backdrops(SkyTestData.look(g)))
            val want = g.backdrops().let { SkyTheme.scores(it) }
            worst = maxOf(worst, abs(q.darkInk.score - want.darkInk.score), abs(q.lightInk.score - want.lightInk.score))
        }
        println("Наибольшее расхождение оценок текста: $worst")
        assertTrue("оценки расходятся на $worst", worst <= 1.2)
    }
}
