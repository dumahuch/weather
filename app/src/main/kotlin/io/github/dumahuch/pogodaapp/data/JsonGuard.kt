package io.github.dumahuch.pogodaapp.data

/**
 * Защита от ответа с чрезмерной вложенностью: разбор такого JSON уходит в рекурсию и кончается `StackOverflowError`,
 * а это ошибка, а не исключение, и приложение упало бы. Настоящие ответы вложены не глубже пяти уровней.
 */
internal object JsonGuard {

    private const val MAX_DEPTH = 32

    /** Истина, если скобки в тексте вложены глубже допустимого. Содержимое строк не считается. */
    fun tooDeep(json: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> if (++depth > MAX_DEPTH) return true
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return false
    }
}
