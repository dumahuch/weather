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
     * Строки, которые по правилу идут строчными: единицы измерения и продолжения после « · » (возраст данных на плашке
     * «Нет сети · данные 3 часа назад», время восхода и захода луны, первое из которых код поднимает до заглавной).
     */
    private val continuations = setOf(
        "mm_unit", "kmh_unit", "hpa_unit", "data_age_under_hour", "data_age_hours", "moonrise_at", "moonset_at",
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
}
