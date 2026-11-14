package io.github.dumahuch.pogodaapp.map

import io.github.dumahuch.pogodaapp.data.Bzip2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Живая проверка настоящих файлов DWD: скачивает свежие `t_2m`, `u_10m`, `v_10m` и `clct_mod` ICON-D2 и прогоняет их
 * через наш разбор. Без сети и без `DWD_LIVE=1` пропускается (обычные тесты в сеть не ходят); в сборке на GitHub
 * запускается отдельным шагом, который не блокирует слияние. Нужна после 0.8.36: тогда новый источник вышел без
 * проверки на настоящем файле и не заработал.
 */
class LiveDwdFilesTest {

    private fun get(url: String): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "PogodaApp-CI")
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        check(connection.responseCode == 200) { "HTTP ${connection.responseCode} для $url" }
        return connection.inputStream.use { it.readBytes() }
    }

    private class Stats(val min: Float, val max: Float, val mean: Double, val spread: Double)

    private fun stats(grid: LatLonGrid): Stats {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var sum = 0.0
        var count = 0
        for (v in grid.values) {
            if (v.isNaN()) continue
            if (v < min) min = v
            if (v > max) max = v
            sum += v
            count++
        }
        val mean = sum / count
        var squares = 0.0
        for (v in grid.values) if (!v.isNaN()) squares += (v - mean) * (v - mean)
        return Stats(min, max, mean, sqrt(squares / count))
    }

    private fun describe(name: String, grid: LatLonGrid): Stats {
        val s = stats(grid)
        println(
            "LIVE $name: ${grid.columns}x${grid.rows}, запад ${grid.west}, юг ${grid.south}, шаг ${grid.stepLon}/${grid.stepLat}, " +
                "min ${s.min}, max ${s.max}, среднее ${"%.2f".format(s.mean)}, разброс ${"%.2f".format(s.spread)}, конец интервала ${grid.validEnd}",
        )
        assertEquals("$name: сетка по долготе", 1215, grid.columns)
        assertEquals("$name: сетка по широте", 746, grid.rows)
        assertEquals("$name: шаг", 0.02, grid.stepLon, 1e-9)
        return s
    }

    @Test
    fun realTemperatureWindAndCloudFilesAreUnderstood() {
        assumeTrue("живая проверка: DWD_LIVE=1", System.getenv("DWD_LIVE") == "1")
        val now = Instant.now()
        val hour = MapTimeline.floorTo(now, Duration.ofHours(1))
        val loader = FieldLoader(
            listings = FolderListings { url -> String(get(url), Charsets.UTF_8) },
            bytes = { url -> get(url) },
            unpack = Bzip2::unpack,
        )
        println("LIVE час $hour, сейчас $now, прогоны ${IconD2Files.candidates(hour, now).map { it.run.toString() + "+" + it.lead }}")

        val temperature = loader.loadAll(hour, now, listOf(IconD2Files.TEMPERATURE))
        assertNotNull("t_2m: нет файла ни в одном прогоне", temperature)
        val t = describe("t_2m", temperature!!.first())
        // Кельвины: от −45 до +50 °C это 228…323 К; в градусах от −45 до 50.
        assertTrue("t_2m: значения не похожи на температуру (${t.min}…${t.max})", t.min > -60f && t.max < 330f && t.max - t.min > 3f)
        val celsius = TemperatureField.celsius(temperature.first())
        val c = stats(celsius)
        println("LIVE t_2m в градусах: min ${c.min}, max ${c.max}, среднее ${"%.2f".format(c.mean)}")
        assertTrue("t_2m: после пересчёта не градусы Цельсия", c.min > -50f && c.max < 55f)
        val frame = TemperatureField.encode(temperature.first())
        val json = TemperatureField.numbersJson(frame)
        val numbers = json.split("\"Feature\"").size - 1
        println("LIVE чисел температуры на карте: $numbers из ${PointGrid.COUNT}, первая пара значков ${json.take(160)}")
        assertTrue("слишком мало чисел температуры: $numbers", numbers > PointGrid.COUNT * 9 / 10)

        val wind = loader.loadAll(hour, now, listOf(IconD2Files.WIND_U, IconD2Files.WIND_V))
        assertNotNull("u_10m и v_10m: нет общего прогона", wind)
        val u = describe("u_10m", wind!![0])
        val v = describe("v_10m", wind[1])
        for (s in listOf(u, v)) {
            assertTrue("ветер: значения не похожи на м/с (${s.min}…${s.max})", abs(s.min) < 90f && abs(s.max) < 90f && s.spread > 0.05)
        }
        val windFrame = WindField.encode(wind[0], wind[1])
        val arrows = WindField.arrowsJson(windFrame)
        val count = arrows.split("\"Feature\"").size - 1
        val speeds = (0 until PointGrid.COUNT).mapNotNull { WindField.at(windFrame, it)?.first }
        println("LIVE стрелок ветра: $count, сила км/ч: среднее ${"%.1f".format(speeds.average())}, наибольшая ${"%.1f".format(speeds.max())}")
        assertTrue("слишком мало стрелок: $count", count > PointGrid.COUNT * 9 / 10)

        val clouds = loader.load(hour, now)
        assertNotNull("clct_mod: нет файла", clouds)
        val k = describe("clct_mod", clouds!!)
        assertTrue("облачность вне 0…1 после нормализации", k.min >= -0.01f && k.max <= 1.01f)

        // Точка Ганновера (52,37 с.ш., 9,73 в.д.): число, которое можно сверить с погодой на глаз.
        val gx = (9.73 - celsius.west) / celsius.stepLon
        val gy = (52.37 - celsius.south) / celsius.stepLat
        println("LIVE Ганновер: ${"%.1f".format(CloudRaster.sample(celsius, gx, gy))} °C, ветер u=${"%.1f".format(CloudRaster.sample(wind[0], gx, gy))} v=${"%.1f".format(CloudRaster.sample(wind[1], gx, gy))} м/с")
    }
}
