package io.github.dumahuch.pogodaapp.sky

import java.io.DataInputStream
import java.io.InputStream

/**
 * Карты яркости: уменьшенные копии 192×128 всех двенадцати картин (небо без облаков и облака),
 * снятые заранее скриптом design/sky/tools/make_maps.py. Приложение считает по ним цвета интерфейса
 * и не читает кадр с экрана. Формат файла описан в скрипте.
 *
 * Каждая карта хранится как RGBA с цветом, умноженным на прозрачность (так удобно накладывать).
 */
internal class SkyMaps private constructor(
    val width: Int,
    val height: Int,
    private val layers: Array<ByteArray>,
) {
    fun layer(scene: SkyScene, clouds: Boolean): ByteArray = layers[scene.ordinal * 2 + if (clouds) 1 else 0]

    companion object {
        const val ASSET_PATH = "sky/maps.bin"
        private const val FORMAT_VERSION = 1

        fun read(input: InputStream): SkyMaps {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also(data::readFully)
            require(String(magic, Charsets.US_ASCII) == "SKYM") { "Это не файл карт яркости неба" }
            val version = data.readUnsignedByte()
            require(version == FORMAT_VERSION) { "Неизвестная версия формата карт: $version" }
            val count = data.readUnsignedByte()
            require(count == SkyScene.entries.size * 2) { "Ожидалось ${SkyScene.entries.size * 2} карт, в файле $count" }
            val width = data.readUnsignedShort()
            val height = data.readUnsignedShort()
            val layers = Array(count) { ByteArray(width * height * 4).also(data::readFully) }
            return SkyMaps(width, height, layers)
        }
    }
}
