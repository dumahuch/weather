package io.github.dumahuch.pogodaapp.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PollenRegionsTest {

    /**
     * Два соседних квадрата по градусу (долгота, широта): район 11 на 9–10 в.д., район 12 на 10–11 в.д., обе на 52–53 с.ш.;
     * район 20 это квадрат 12–13 в.д. с дырой посередине; район 31 состоит из двух отдельных многоугольников.
     */
    private val json = """
        {"regions":[
          {"id":11,"polygons":[[[9,52, 10,52, 10,53, 9,53]]]},
          {"id":12,"polygons":[[[10,52, 11,52, 11,53, 10,53]]]},
          {"id":20,"polygons":[[[12,52, 13,52, 13,53, 12,53],[12.4,52.4, 12.6,52.4, 12.6,52.6, 12.4,52.6]]]},
          {"id":31,"polygons":[[[14,50, 14.1,50, 14.1,50.1, 14,50.1]],[[15,50, 15.1,50, 15.1,50.1, 15,50.1]]]}
        ]}
    """.trimIndent()

    private val regions = checkNotNull(PollenRegions.parse(json))

    @Test
    fun pointInsideARegionGivesItsNumber() {
        assertEquals(11, regions.keyOf(52.5, 9.5))
        assertEquals(12, regions.keyOf(52.5, 10.5))
    }

    @Test
    fun aHoleIsNotPartOfTheRegion() {
        assertEquals(20, regions.keyOf(52.2, 12.2))
        // В дыре региона нет, ближайшая граница района 20 меньше 10 км (дыра шириной 0,2°): ответ всё равно район 20.
        assertEquals(20, regions.keyOf(52.5, 12.5))
    }

    @Test
    fun aRegionMayConsistOfSeveralPolygons() {
        assertEquals(31, regions.keyOf(50.05, 14.05))
        assertEquals(31, regions.keyOf(50.05, 15.05))
    }

    @Test
    fun pointJustOutsideTheBorderTakesTheNearestRegion() {
        // Около 5 км к югу от нижней границы района 11 (0,045° широты это около 5 км).
        assertEquals(11, regions.keyOf(51.955, 9.5))
    }

    @Test
    fun pointFarFromEveryRegionIsNotInGermany() {
        assertNull(regions.keyOf(48.85, 2.35)) // Париж
        assertNull(regions.keyOf(51.5, 9.5)) // около 55 км к югу от района 11
    }

    @Test
    fun brokenFileIsNotParsed() {
        assertNull(PollenRegions.parse("не json"))
        assertNull(PollenRegions.parse("""{"regions":[{"id":1}]}"""))
        assertNotNull(PollenRegions.parse("""{"regions":[]}"""))
    }

    @Test
    fun emptyListOfRegionsKnowsNothing() {
        assertNull(PollenRegions.parse("""{"regions":[]}""")?.keyOf(52.5, 9.5))
    }
}
