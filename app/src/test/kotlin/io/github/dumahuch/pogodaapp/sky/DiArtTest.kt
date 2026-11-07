package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/** Геометрия, файлы и карты фона «Ди». */
class DiArtTest {

    private val maps: DiMaps by lazy { FileInputStream(File("src/main/assets/${DiMaps.ASSET_PATH}")).use { DiMaps.read(it) } }

    private val screens = listOf(412.0 to 892.0, 360.0 to 800.0, 1440.0 to 3120.0, 600.0 to 900.0)
    private val pans = listOf(0.0, 0.2, 0.5, 0.8, 1.0)
    private val lifts = listOf(-0.3, -0.1, -0.04, 0.0, 0.05, 0.1, 0.3)

    @Test
    fun everyLayerCoversTheWholeScreenAtAnyPanAndLift() {
        for ((w, h) in screens) for (pan in pans) for (lift in lifts) for (layer in DiArt.Layer.entries) {
            val p = DiArt.place(layer, w, h, pan, lift)
            val right = p.left + DiArt.CANVAS_W * p.scale
            val bottom = p.top + DiArt.CANVAS_H * p.scale
            val where = "$layer, экран ${w}×$h, ход $pan, подъём $lift"
            assertTrue("слева дыра: $where", p.left <= 1e-9)
            assertTrue("справа дыра: $where", right >= w - 1e-9)
            assertTrue("снизу дыра: $where", bottom >= h - 1e-9)
            // Небо стоит на месте, сверху у него дыры нет; гряды сверху закрыты слоями за ними.
            if (layer == DiArt.Layer.Sky) assertEquals(0.0, p.top, 1e-9)
        }
    }

    @Test
    fun nearRidgesMoveMoreThanFarOnes() {
        val w = 412.0
        val h = 892.0
        var lastX = 0.0
        var lastY = 0.0
        for (layer in DiArt.Layer.entries) {
            val dx = DiArt.place(layer, w, h, 0.9, 0.0).left - DiArt.place(layer, w, h, 0.1, 0.0).left
            val dy = DiArt.place(layer, w, h, 0.5, DiArt.MAX_LIFT).top - DiArt.place(layer, w, h, 0.5, 0.0).top
            assertTrue("$layer по горизонтали", -dx >= lastX - 1e-9)
            assertTrue("$layer по вертикали", -dy >= lastY - 1e-9)
            lastX = -dx
            lastY = -dy
        }
        assertTrue("ближняя гряда при прокрутке поднимается", lastY > 50)
    }

    @Test
    fun liftFollowsScrollAndStopsAtTheLimit() {
        assertEquals(0.0, DiArt.liftOf(0.0, 2000.0), 1e-12)
        assertEquals(0.025, DiArt.liftOf(200.0, 2000.0), 1e-12)
        assertEquals(DiArt.MAX_LIFT, DiArt.liftOf(100_000.0, 2000.0), 1e-12)
        assertEquals(-DiArt.MAX_LIFT, DiArt.liftOf(-100_000.0, 2000.0), 1e-12)
        assertEquals(0.0, DiArt.liftOf(300.0, 0.0), 1e-12)
    }

    @Test
    fun panStaysCloserToTheMiddleThanNeutral() {
        for (scene in SkyScene.entries) {
            val weights = SceneWeights(DoubleArray(SkyScene.entries.size) { if (it == scene.ordinal) 1.0 else 0.0 })
            val di = DiArt.pan(weights)
            assertTrue("$scene", kotlin.math.abs(di - 0.5) <= kotlin.math.abs(scene.pan - 0.5) + 1e-12)
        }
    }

    @Test
    fun stormSheetIsForDarkSkiesOnly() {
        for (c in listOf(Condition.Clear, Condition.Partly, Condition.Snow, Condition.Fog)) {
            assertEquals("$c", 0.0, SkyWeather.of(c).darkShare, 0.0)
        }
        for (c in listOf(Condition.Rain, Condition.Storm)) {
            assertEquals("$c", 1.0, SkyWeather.of(c).darkShare, 0.0)
        }
        // Пасмурно без процента облачности: светлые облака, лишь одно из десяти на грозовом листе (step-29).
        assertEquals(0.1, SkyWeather.of(Condition.Cloudy).darkShare, 0.0)
    }

    @Test
    fun tintOfAMixLiesBetweenTheTwoScenes() {
        val values = DoubleArray(SkyScene.entries.size)
        values[SkyScene.Afternoon.ordinal] = 0.5
        values[SkyScene.Night.ordinal] = 0.5
        val t = DiArt.tint(SceneWeights(values))
        val day = DiArt.tint(SkyScene.Afternoon)
        val night = DiArt.tint(SkyScene.Night)
        assertEquals((day.b + night.b) / 2, t.b, 1e-9)
        assertTrue("ночь темнее дня", luminance(night) < luminance(day))
    }

    @Test
    fun allLayerFilesAndCloudSheetsArePresent() {
        for (scene in SkyScene.entries) for (layer in DiArt.Layer.entries) {
            assertTrue(DiArt.file(scene, layer), File("src/main/assets/sky/di/${DiArt.file(scene, layer)}.webp").isFile)
        }
        for (name in listOf("clouds_light", "clouds_storm")) assertTrue(name, File("src/main/assets/sky/di/$name.webp").isFile)
    }

    @Test
    fun mapsHaveOpaqueSkyRidgesWithSkyAboveAndCloudsWithGaps() {
        assertEquals(64, maps.layerW)
        assertEquals(96, maps.layerH)
        for (scene in SkyScene.entries) {
            val sky = maps.layer(scene, DiArt.Layer.Sky)
            for (i in 3 until sky.size step 4) assertEquals("${DiArt.prefix(scene)}: небо сплошное", 255, sky[i].toInt() and 0xFF)
            for (layer in DiArt.Layer.entries.drop(1)) {
                val m = maps.layer(scene, layer)
                val topAlpha = m[3].toInt() and 0xFF
                val bottomAlpha = m[(maps.layerW * (maps.layerH - 1)) * 4 + 3].toInt() and 0xFF
                assertEquals("${DiArt.file(scene, layer)}: сверху прозрачно", 0, topAlpha)
                assertEquals("${DiArt.file(scene, layer)}: внизу сплошное", 255, bottomAlpha)
            }
        }
        for (storm in listOf(false, true)) {
            val m = maps.clouds(storm)
            var clear = 0
            var solid = 0
            for (i in 3 until m.size step 4) {
                val a = m[i].toInt() and 0xFF
                if (a == 0) clear++
                if (a > 200) solid++
            }
            assertTrue("облака $storm: просветы", clear > m.size / 4 / 3)
            assertTrue("облака $storm: облака есть", solid > 40)
        }
    }

    @Test
    fun brokenFileIsRejected() {
        val text = "not a map".toByteArray()
        val error = runCatching { DiMaps.read(text.inputStream()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
