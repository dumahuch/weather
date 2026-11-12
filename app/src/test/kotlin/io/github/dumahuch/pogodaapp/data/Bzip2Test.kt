package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.map.MapDataException
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Распаковка файлов DWD: обычный файл, мусор, слишком большой результат. */
class Bzip2Test {

    private fun pack(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BZip2CompressorOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    @Test
    fun unpacksWhatWasPacked() {
        val original = ByteArray(100_000) { (it % 251).toByte() }
        assertArrayEquals(original, Bzip2.unpack(pack(original)))
    }

    @Test
    fun garbageIsDataError() {
        assertThrows(MapDataException::class.java) { Bzip2.unpack("<html>404</html>".toByteArray()) }
        assertThrows(MapDataException::class.java) { Bzip2.unpack(ByteArray(0)) }
    }

    @Test
    fun tooLargeResultIsRefused() {
        // Сжатые нули: маленький файл, огромный результат (защита от «бомбы»).
        val bomb = pack(ByteArray(2_000_000))
        assertThrows(MapDataException::class.java) { Bzip2.unpack(bomb, maxBytes = 1_000_000) }
    }
}
