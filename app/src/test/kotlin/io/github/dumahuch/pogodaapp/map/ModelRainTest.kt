package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Дождь ICON-D2: сила дождя за 15 минут из разности накоплений, выбор файлов, запас файлов. */
class ModelRainTest {

    private val now = Instant.parse("2026-11-12T16:30:00Z")
    private val run = Instant.parse("2026-11-12T15:00:00Z")

    /** Файл часа [lead]: четыре поля на :00, :15, :30, :45 с одинаковым накоплением по всей сетке (сотые мм). */
    private fun file(lead: Int, hundredths: List<Int>): ByteArray {
        val hour = LocalDateTime.ofInstant(run.plus(Duration.ofHours(lead.toLong())), ZoneOffset.UTC)
        return hundredths.mapIndexed { k, v ->
            // Сетка 25 × 12 узлов по 1° от 4° з.д. и 47° с.ш.: накрывает середину области карты.
            Grib2Test.message(columns = 25, rows = 12, raw = IntArray(25 * 12) { v }, scan = 64, validEnd = hour.plusMinutes(15L * k))
        }.reduce { a, b -> a + b }
    }

    private val files = mapOf(
        IconD2Rain.fileName(run, 6) to file(6, listOf(50, 60, 70, 90)),
        IconD2Rain.fileName(run, 7) to file(7, listOf(100, 120, 150, 160)),
    )

    private fun loader(downloads: MutableList<String>, listing: String = files.keys.joinToString("") { "<a href=\"$it\">" }) =
        ModelRainLoader(
            listings = FolderListings { url -> if (url.endsWith("/15/tot_prec/")) listing else "" },
            bytes = { url -> downloads += url; files.getValue(url.substringAfterLast('/')) },
            unpack = { it },
            width = 16,
            height = 12,
        )

    /** Уровень пикселя в середине картинки (около 51° с.ш., 11° в.д.); накопление хранится в 1/128 мм, отсюда допуск в уровень. */
    private fun middle(levels: ByteArray) = levels[6 * 16 + 8].toInt() and 0xFF

    @Test
    fun quarterInsideHourComesFromOneFile() {
        val downloads = mutableListOf<String>()
        val levels = loader(downloads).load(Instant.parse("2026-11-12T22:30:00Z"), now)!!
        // 1,50 − 1,20 мм за 15 минут = 1,2 мм/ч.
        assertEquals(RainPalette.levelOf(1.2).toDouble(), middle(levels).toDouble(), 1.0)
        assertEquals(listOf(IconD2Rain.folder(run) + IconD2Rain.fileName(run, 7)), downloads)
    }

    @Test
    fun quarterOnTheHourTakesLastFieldOfPreviousFile() {
        val downloads = mutableListOf<String>()
        val model = loader(downloads)
        val levels = model.load(Instant.parse("2026-11-12T22:00:00Z"), now)!!
        // 1,00 − 0,90 мм за 15 минут = 0,4 мм/ч.
        assertEquals(RainPalette.levelOf(0.4).toDouble(), middle(levels).toDouble(), 1.0)
        assertEquals(2, downloads.size)
        // Остальные четверти часа берут файлы из запаса.
        model.load(Instant.parse("2026-11-12T22:45:00Z"), now)
        model.load(Instant.parse("2026-11-12T21:30:00Z"), now)
        assertEquals(2, downloads.size)
    }

    @Test
    fun noFileInAnyRunGivesNothing() {
        assertNull(loader(mutableListOf(), listing = "").load(Instant.parse("2026-11-12T22:30:00Z"), now))
    }

    @Test
    fun rateIsNeverNegative() {
        assertEquals(0.0, IconD2Rain.rate(1.0, 0.9), 0.0)
        assertEquals(2.0, IconD2Rain.rate(1.0, 1.5), 1e-9)
        assertEquals(7, IconD2Rain.leadOf(run, Instant.parse("2026-11-12T22:45:00Z")))
    }
}
