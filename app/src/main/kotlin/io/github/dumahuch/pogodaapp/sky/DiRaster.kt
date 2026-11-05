package io.github.dumahuch.pogodaapp.sky

import java.io.DataInputStream
import java.io.InputStream

/**
 * Карты яркости фона «Ди»: уменьшенные копии всех слоёв (64×96) и двух листов облаков (128×48), блоки 16×16.
 * Делает design/sky/di/tools/make_di.py, формат описан там. Цвет умножен на прозрачность.
 */
internal class DiMaps private constructor(
    val layerW: Int,
    val layerH: Int,
    private val layers: Array<ByteArray>,
    val sheetW: Int,
    val sheetH: Int,
    private val sheets: Array<ByteArray>,
) {
    fun layer(scene: SkyScene, layer: DiArt.Layer): ByteArray = layers[scene.ordinal * DiArt.Layer.entries.size + layer.ordinal]

    fun clouds(storm: Boolean): ByteArray = sheets[if (storm) 1 else 0]

    companion object {
        const val ASSET_PATH = "sky/di/maps.bin"
        private const val FORMAT_VERSION = 1

        fun read(input: InputStream): DiMaps {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also(data::readFully)
            require(String(magic, Charsets.US_ASCII) == "SKYD") { "Это не файл карт фона «Ди»" }
            val version = data.readUnsignedByte()
            require(version == FORMAT_VERSION) { "Неизвестная версия формата карт: $version" }
            val scenes = data.readUnsignedByte()
            val perScene = data.readUnsignedByte()
            require(scenes == SkyScene.entries.size && perScene == DiArt.Layer.entries.size) {
                "Ожидалось ${SkyScene.entries.size} сцен по ${DiArt.Layer.entries.size} слоёв, в файле $scenes по $perScene"
            }
            val lw = data.readUnsignedShort()
            val lh = data.readUnsignedShort()
            val sheetCount = data.readUnsignedByte()
            require(sheetCount == 2) { "Ожидалось 2 листа облаков, в файле $sheetCount" }
            val sw = data.readUnsignedShort()
            val sh = data.readUnsignedShort()
            val layers = Array(scenes * perScene) { ByteArray(lw * lh * 4).also(data::readFully) }
            val sheets = Array(sheetCount) { ByteArray(sw * sh * 4).also(data::readFully) }
            return DiMaps(lw, lh, layers, sw, sh, sheets)
        }
    }
}

/**
 * Кадр фона «Ди» тем же порядком, что рисует экран ([io.github.dumahuch.pogodaapp.ui.sky.DiPainter]):
 * небо → облака (подкрашенные, с прозрачностью слоя) → погодная вуаль → пять гряд сзади вперёд → серость → затемнение → приглушение.
 * Вуаль ложится поверх облаков (у «Нейтрального» под ними): тёмные тучи на светлой вуали под цифрами давали пёстрый фон
 * (контраст цифр 2,7 : 1 утром в пасмурно), дымка поверх выравнивает небо; гряды остаются чёткими.
 * Каждый слой смешивается между двумя временами суток по долям (композиция гряд у всех сцен одна).
 */
internal class DiRaster(private val maps: DiMaps) : SkyRaster {

    private val tmp = DoubleArray(4)
    private val layerColor = DoubleArray(4)
    private val rgb = DoubleArray(3)

    override fun render(look: SkyLook, out: DoubleArray) {
        val gw = SkySampler.GRID_W
        val gh = SkySampler.GRID_H
        val sw = SkySampler.SCENE_W
        val sh = SkySampler.SCENE_H
        val scenes = look.weights.visible()
        val w = look.weights
        val pan = DiArt.pan(w)
        val places = DiArt.Layer.entries.map { DiArt.place(it, sw, sh, pan, look.lift) }
        val cloudPlace = DiArt.placeClouds(sw, sh, look.cloudPosition)
        val storm = DiArt.storm(look.weather)
        val cloudMap = maps.clouds(storm)
        val clouds = CloudLayout.of(look.weather, storm)
        val tint = DiArt.tint(w)
        val veilStrength = look.veilStrength
        // Размер карты в пикселях холста: слой 1024 / 64 = 16.
        val layerK = maps.layerW / DiArt.CANVAS_W
        val sheetK = maps.sheetW / DiArt.SHEET_W

        for (py in 0 until gh) {
            val sy = (py + 0.5) / gh * sh
            val rowVeil = RasterKit.veilAt(look.veil, sy / sh)
            for (px in 0 until gw) {
                val sx = (px + 0.5) / gw * sw
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (layer in DiArt.Layer.entries) {
                    val p = places[layer.ordinal]
                    val mx = (sx - p.left) / p.scale * layerK
                    val my = (sy - p.top) / p.scale * layerK
                    mixScenes(scenes, w, layer, mx, my)
                    val a = layerColor[3] / 255.0
                    r = layerColor[0] + (1 - a) * r; g = layerColor[1] + (1 - a) * g; b = layerColor[2] + (1 - a) * b
                    if (layer == DiArt.Layer.Sky) {
                        // Точка в пикселях листа облаков; облака дальние первыми, как при рисовании.
                        val u = (sx - cloudPlace.left) / cloudPlace.scale
                        val v = (sy - cloudPlace.top) / cloudPlace.scale
                        for (cloud in clouds) {
                            val lx = (u - cloud.left) / cloud.scale
                            val ly = (v - cloud.top) / cloud.scale
                            if (lx < 0 || ly < 0 || lx >= cloud.rect.w || ly >= cloud.rect.h) continue
                            val ix = if (cloud.mirrored) cloud.rect.w - lx else lx
                            RasterKit.bilinearOrClear(cloudMap, maps.sheetW, maps.sheetH, (cloud.rect.x + ix) * sheetK, (cloud.rect.y + ly) * sheetK, tmp)
                            val ca = cloud.alpha * tmp[3] / 255.0
                            r = cloud.alpha * tmp[0] * tint.r / 255.0 + (1 - ca) * r
                            g = cloud.alpha * tmp[1] * tint.g / 255.0 + (1 - ca) * g
                            b = cloud.alpha * tmp[2] * tint.b / 255.0 + (1 - ca) * b
                        }
                        if (veilStrength > 0.01) {
                            r = lerp(r, rowVeil.r, veilStrength); g = lerp(g, rowVeil.g, veilStrength); b = lerp(b, rowVeil.b, veilStrength)
                        }
                    }
                }
                rgb[0] = r; rgb[1] = g; rgb[2] = b
                RasterKit.finish(look, rgb)
                val i = (py * gw + px) * 3
                out[i] = rgb[0]; out[i + 1] = rgb[1]; out[i + 2] = rgb[2]
            }
        }
    }

    /** Цвет слоя [layer] в точке карты (mx, my), смешанный между временами суток по долям; результат в [layerColor]. */
    private fun mixScenes(scenes: List<SkyScene>, w: SceneWeights, layer: DiArt.Layer, mx: Double, my: Double) {
        layerColor.fill(0.0)
        var acc = 0.0
        for (scene in scenes) {
            val weight = w[scene]
            acc += weight
            RasterKit.bilinear(maps.layer(scene, layer), maps.layerW, maps.layerH, mx, my, tmp)
            val k = weight / acc
            for (c in 0..3) layerColor[c] = lerp(layerColor[c], tmp[c], k)
        }
    }
}
