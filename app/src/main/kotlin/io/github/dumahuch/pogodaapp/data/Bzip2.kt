package io.github.dumahuch.pogodaapp.data

import io.github.dumahuch.pogodaapp.map.MapDataException
import io.github.dumahuch.pogodaapp.weather.ForecastException
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.ByteArrayInputStream
import java.io.IOException

/** Распаковка файлов DWD (bzip2, библиотека Apache Commons Compress) с пределом размера и времени. */
internal object Bzip2 {

    /** Поле ICON-D2 после распаковки около 1,8 МБ; больше этого не бывает. */
    const val MAX_UNPACKED_BYTES = 8 * 1024 * 1024

    private const val TIMEOUT_NANOS = 20_000_000_000L

    fun unpack(packed: ByteArray, maxBytes: Int = MAX_UNPACKED_BYTES): ByteArray = try {
        BZip2CompressorInputStream(ByteArrayInputStream(packed)).use { stream ->
            HttpJson.readBounded(stream, maxBytes, System.nanoTime() + TIMEOUT_NANOS)
        }
    } catch (e: IOException) {
        throw MapDataException("bzip2: ${e.message}")
    } catch (e: ForecastException) {
        throw MapDataException("bzip2: ${e.message}")
    }
}
