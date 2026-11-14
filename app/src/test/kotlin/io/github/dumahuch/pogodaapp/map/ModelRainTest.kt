package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val levels = loader(downloads).load(Instant.parse("2026-11-12T22:30:00Z"), now)!!.levels
        // 1,50 − 1,20 мм за 15 минут = 1,2 мм/ч.
        assertEquals(RainPalette.levelOf(1.2).toDouble(), middle(levels).toDouble(), 1.0)
        assertEquals(listOf(IconD2Rain.folder(run) + IconD2Rain.fileName(run, 7)), downloads)
    }

    @Test
    fun quarterOnTheHourTakesLastFieldOfPreviousFile() {
        val downloads = mutableListOf<String>()
        val model = loader(downloads)
        val levels = model.load(Instant.parse("2026-11-12T22:00:00Z"), now)!!.levels
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
    fun fileOfMomentIsWholeHoursFromRun() {
        assertEquals(7, IconD2RainModel.leadOf(run, Instant.parse("2026-11-12T22:45:00Z")))
        assertEquals(8, IconD2RainModel.leadOf(run, Instant.parse("2026-11-12T23:00:00Z")))
    }

    @Test
    fun lessRainLaterGivesNoRainNotNegative() {
        val downloads = mutableListOf<String>()
        val shrinking = mapOf(IconD2Rain.fileName(run, 7) to file(7, listOf(100, 90, 80, 70)))
        val model = ModelRainLoader(
            listings = FolderListings { shrinking.keys.joinToString("") { "<a href=\"$it\">" } },
            bytes = { url -> downloads += url; shrinking.getValue(url.substringAfterLast('/')) },
            unpack = { it },
            width = 16,
            height = 12,
        )
        assertTrue(model.load(Instant.parse("2026-11-12T22:30:00Z"), now)!!.levels.all { it.toInt() == 0 })
    }

    /** Загрузчик с одним файлом, где дождь (1 мм за 15 минут, 4 мм/ч) только в узлах [wet] сетки 25 × 12 по 1°. */
    private fun spotLoader(wet: Set<Int>): ModelRainLoader {
        val hour = LocalDateTime.ofInstant(run.plus(Duration.ofHours(7)), ZoneOffset.UTC)
        val bytes = listOf(0, 0, 100, 100).mapIndexed { k, v ->
            Grib2Test.message(columns = 25, rows = 12, raw = IntArray(25 * 12) { if (it in wet) v else 0 }, scan = 64, validEnd = hour.plusMinutes(15L * k))
        }.reduce { a, b -> a + b }
        val name = IconD2Rain.fileName(run, 7)
        // 8 пикселей на градус по долготе: узел 11° попадает между пикселями 67 и 68.
        return ModelRainLoader(
            listings = FolderListings { "<a href=\"$name\">" },
            bytes = { bytes },
            unpack = { it },
            width = 128,
            height = 12,
        )
    }

    /** Узел сетки 25 × 12: 11° в.д. и 51° с.ш. */
    private val middleNode = 4 * 25 + 15

    @Test
    fun spotOfOneOrTwoNodesIsNotShown() {
        val at = Instant.parse("2026-11-12T22:30:00Z")
        assertTrue(spotLoader(setOf(middleNode)).load(at, now)!!.levels.all { it.toInt() == 0 })
        assertTrue(spotLoader(setOf(middleNode, middleNode + 26)).load(at, now)!!.levels.all { it.toInt() == 0 })
    }

    @Test
    fun smallSpotStaysSoftNotSquare() {
        // Пятно в три узла по долготе с серединой в 11° в.д.
        val frame = spotLoader(setOf(middleNode - 1, middleNode, middleNode + 1)).load(Instant.parse("2026-11-12T22:30:00Z"), now)!!
        assertEquals(run, frame.run)
        val levels = frame.levels
        // Строка картинки около 51° с.ш.
        val row = (0 until 12).maxByOrNull { y -> levels[y * 128 + 67].toInt() and 0xFF }!!
        val line = IntArray(128) { levels[row * 128 + it].toInt() and 0xFF }
        // Край пятна за последним мокрым узлом (12° в.д., пиксель 75…76): дождь слабеет плавно, без ступеньки.
        val edge = 76
        assertTrue(line[edge] > line[edge + 3] && line[edge + 3] > line[edge + 6] && line[edge + 6] > 0)
        assertEquals(0, line[edge + 12])
        assertTrue(line[67] > 0)
    }

    @Test
    fun runForNamesRunWithoutDownloading() {
        val downloads = mutableListOf<String>()
        assertEquals(run, loader(downloads).runFor(Instant.parse("2026-11-12T22:30:00Z"), now))
        assertTrue(downloads.isEmpty())
        assertNull(loader(downloads, listing = "").runFor(Instant.parse("2026-11-12T22:30:00Z"), now))
    }
}
