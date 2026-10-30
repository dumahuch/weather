package io.github.dumahuch.pogodaapp.forecast

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TextCaseTest {

    private val ru = Locale.forLanguageTag("ru")
    private val de = Locale.forLanguageTag("de")
    private val en = Locale.ENGLISH

    @Test
    fun sentenceCaseRaisesTheFirstLetterOnly() {
        assertEquals("Восход Луны 07:12", sentenceCase("восход Луны 07:12", ru))
        assertEquals("Moonrise 07:12", sentenceCase("moonrise 07:12", en))
        assertEquals("Mondaufgang 07:12", sentenceCase("Mondaufgang 07:12", de))
        assertEquals("", sentenceCase("", en))
    }

    @Test
    fun continuationLowersRussianAndEnglish() {
        assertEquals("умеренный ветер", continuationCase("Умеренный ветер", ru))
        assertEquals("шторм", continuationCase("Шторм", ru))
        assertEquals("fresh breeze", continuationCase("Fresh breeze", en))
        assertEquals("storm", continuationCase("Storm", en))
    }

    @Test
    fun germanSingleWordsAreNounsAndKeepTheirCapital() {
        assertEquals("Sturm", continuationCase("Sturm", de))
        assertEquals("Orkan", continuationCase("Orkan", de))
        assertEquals("Windstille", continuationCase("Windstille", de))
    }

    @Test
    fun germanPhrasesStartWithAnAdjectiveAndGoLower() {
        assertEquals("mäßige Brise", continuationCase("Mäßige Brise", de))
        assertEquals("starker Wind", continuationCase("Starker Wind", de))
        assertEquals("orkanartiger Sturm", continuationCase("Orkanartiger Sturm", de))
    }

    @Test
    fun abbreviationsKeepTheirCapitals() {
        assertEquals("UV index", continuationCase("UV index", en))
        assertEquals("УФ-индекс", continuationCase("УФ-индекс", ru))
    }

    @Test
    fun emptyTextStaysEmpty() {
        assertEquals("", continuationCase("", ru))
    }
}
