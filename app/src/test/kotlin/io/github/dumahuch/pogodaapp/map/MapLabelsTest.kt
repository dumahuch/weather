package io.github.dumahuch.pogodaapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Какие подписи подложки остаются на карте. */
class MapLabelsTest {

    @Test
    fun cityAndMunicipalityNamesStay() {
        assertTrue(MapLabels.isKept("Name_Landeshauptstadt"))
        assertTrue(MapLabels.isKept("Name_Stadtgemeinde_bis_100000"))
        assertTrue(MapLabels.isKept("Name_Landgemeinde_bis_5000"))
    }

    @Test
    fun roadsWatersLandscapesStatesAndSymbolsGo() {
        listOf(
            "Nummer_Autobahn", "Name_Bundesland", "Name_Landschaft_gross", "Name_GewaesserF_gross", "Name_Wald",
            "Name_Ortsteil_Stadtteil_gross", "Name_Wohnplatz_klein", "Symbol_Windkraftanlage", "Hauskoordinate",
        ).forEach { assertFalse(it, MapLabels.isKept(it)) }
    }

    @Test
    fun onlyExtraLabelsAreHidden() {
        val ids = listOf("Name_Bundesland", "Name_Stadtgemeinde_bis_50000", "Nummer_Autobahn", "Name_Landeshauptstadt")
        assertEquals(listOf("Name_Bundesland", "Nummer_Autobahn"), MapLabels.hidden(ids))
    }

    @Test
    fun nothingIsHiddenWhenTheStyleHasNoKnownCityLayer() {
        // Стиль мог переименовать слои: лучше оставить все подписи, чем стереть их все.
        assertEquals(emptyList<String>(), MapLabels.hidden(listOf("Name_Bundesland", "Nummer_Autobahn")))
        assertEquals(emptyList<String>(), MapLabels.hidden(emptyList()))
    }
}
