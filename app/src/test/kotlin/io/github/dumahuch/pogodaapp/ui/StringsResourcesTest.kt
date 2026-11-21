package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Проверка строк интерфейса во всех трёх языках: единое правило регистра (каждая отдельная строка начинается с заглавной,
 * решение Димы 30.10.2026), одинаковый набор строк и одинаковые подстановки в русском, немецком и английском.
 */
class StringsResourcesTest {

    private class Entry(val name: String, val values: List<String>, val isPlural: Boolean)

    private fun resFile(dir: String): File {
        val local = File("src/main/res/$dir/strings.xml")
        return if (local.isFile) local else File("app/src/main/res/$dir/strings.xml")
    }

    private fun load(dir: String): Map<String, Entry> {
        val file = resFile(dir)
        assertTrue("нет файла ${file.path}", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val result = LinkedHashMap<String, Entry>()
        val children = root.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name")
            result[name] = when (element.tagName) {
                "string" -> Entry(name, listOf(element.textContent), isPlural = false)
                "string-array" -> Entry(name, items(element), isPlural = false)
                "plurals" -> Entry(name, items(element), isPlural = true)
                else -> continue
            }
        }
        return result
    }

    private fun items(element: Element): List<String> {
        val nodes = element.getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it).textContent }
    }

    private val languages = mapOf("en" to "values", "ru" to "values-ru", "de" to "values-de")

    /**
     * Строки, которые по правилу идут строчными: единицы измерения (и подписи легенды карты «мм/ч» и «км/ч») и продолжения после « · » (возраст данных на плашке
     * «Нет сети · данные 3 часа назад», время восхода и захода луны, первое из которых код поднимает до заглавной;
     * части суток подставляются в «Осадки %1$s»), время на плашке предупреждения справа («до 06:00», как в макете) и слова
     * «сегодня» и «завтра» в середине периода предупреждения («Сегодня 15:00 – завтра 06:00»), а также «с юга» и подобные: они идут после слова о силе ветра («Умеренный ветер с юга»).
     */
    private val continuations = setOf(
        "mm_unit", "kmh_unit", "pressure_unit", "km_unit", "map_legend_unit", "map_legend_unit_wind", "data_age_under_hour", "data_age_hours", "moonrise_at", "moonset_at",
        "precip_day_parts", "alert_starts_in", "alert_from", "alert_from_tomorrow", "alert_until", "alert_until_tomorrow",
        "alert_until_day", "alert_day_today_mid", "alert_day_tomorrow_mid", "wind_from",
        // Страницы подробностей (0.8.72): подписи легенд, шкал и частей суток, слова в середине фразы.
        "metric_at", "metric_legend_wind", "metric_legend_gusts", "metric_legend_blows", "metric_legend_rain", "metric_legend_chance",
        "metric_legend_humidity", "metric_legend_dew", "metric_days_wind", "metric_days_gusts", "metric_days_wind_note", "metric_gusts_short",
        "metric_days_humidity_note", "metric_daytime", "metric_night", "metric_daytime_hours", "metric_night_hours",
        "metric_parts", "metric_parts_when", "metric_and", "metric_beaufort", "metric_calm", "metric_hurricane", "metric_marker_now",
        "metric_marker_gusts", "metric_vis_fog", "metric_vis_far", "rain_intensity", "rain_chance", "metric_week_dry", "metric_dry_chance",
        // «Солнце и Луна» (0.8.77): подписи легенды полосы света.
        "light_day", "light_golden", "light_blue", "light_twilight", "light_night",
        // Продолжение после крупного процента освещённости: «89 % освещено · 12-й день цикла».
        "moon_lit_line",
    )

    @Test
    fun everyStandaloneStringStartsWithACapitalLetterOrANonLetter() {
        val wrong = ArrayList<String>()
        for ((language, dir) in languages) {
            for (entry in load(dir).values) {
                if (entry.name in continuations) continue
                for (value in entry.values) {
                    val first = value.trim().firstOrNull() ?: continue
                    if (first.isLetter() && !first.isUpperCase()) wrong += "$language/${entry.name}: «$value»"
                }
            }
        }
        assertTrue("строки с маленькой буквы в начале: $wrong", wrong.isEmpty())
    }

    @Test
    fun allLanguagesHaveTheSameStrings() {
        val english = load("values")
        for ((language, dir) in languages) {
            val other = load(dir)
            assertEquals("набор строк $language", english.keys, other.keys)
            for ((name, entry) in english) {
                // у числительных в русском больше форм, чем в английском: число пунктов сверяется у остальных
                if (!entry.isPlural) assertEquals("число пунктов $language/$name", entry.values.size, other.getValue(name).values.size)
            }
        }
    }

    @Test
    fun allLanguagesUseTheSamePlaceholders() {
        val placeholder = Regex("""%(?:\d\$)?[sd]""")
        val english = load("values")
        for ((language, dir) in languages) {
            val other = load(dir)
            for ((name, entry) in english) {
                // у числительных в русском больше форм, чем в английском: подстановки сверяются у обычных строк и массивов
                if (entry.isPlural) continue
                val expected = entry.values.map { placeholder.findAll(it).map { m -> m.value }.sorted().toList() }
                val actual = other.getValue(name).values.map { placeholder.findAll(it).map { m -> m.value }.sorted().toList() }
                assertEquals("подстановки $language/$name", expected, actual)
            }
        }
    }

    /** В немецком везде «du» (решение Димы, product.md): вежливая форма («Sie», «Ihr…», «Ihnen») в строках запрещена. */
    @Test
    fun germanAddressesTheUserAsDu() {
        val formal = Regex("""\b(Sie|Ihr|Ihre|Ihren|Ihrem|Ihrer|Ihres|Ihnen)\b""")
        val wrong = load("values-de").values.flatMap { entry ->
            entry.values.filter { formal.containsMatchIn(it) }.map { "${entry.name}: «$it»" }
        }
        assertTrue("в немецком вежливая форма вместо «du»: $wrong", wrong.isEmpty())
    }
}
