package io.github.dumahuch.pogodaapp.forecast

import java.util.Locale

/**
 * Регистр первой буквы по единому правилу строк (русский, немецкий, английский): отдельная строка начинается с заглавной,
 * продолжение после « · » или двоеточия со строчной; в немецком существительные всегда с заглавной.
 */

/** Строка с заглавной первой буквой: когда готовая подпись становится началом отдельной строки. */
fun sentenceCase(text: String, locale: Locale): String =
    text.replaceFirstChar { it.titlecase(locale) }

/**
 * Подпись как продолжение строки (после « · » или двоеточия): первая буква строчная. Не меняется то, что начинается
 * с сокращения из заглавных («УФ-индекс», «UV index»), и в немецком одиночное слово (оно существительное: «Sturm»,
 * «Orkan»); немецкое словосочетание вроде «Mäßige Brise» начинается с прилагательного и тоже становится строчным.
 */
fun continuationCase(text: String, locale: Locale): String {
    if (text.length > 1 && text[1].isUpperCase()) return text
    if (locale.language == "de" && ' ' !in text) return text
    return text.replaceFirstChar { it.lowercase(locale) }
}
