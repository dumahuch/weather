package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkySnapshotTest {

    private fun palette() = PogodaPalette.Neutral.copy(
        ink = Color(0xFFEEF3F9.toInt()),
        accent = Color(0xFFA9C5DD.toInt()),
        infoBackdrop = Color(0xFF2A3446.toInt()),
        darkInk = false,
        smoke = 0.75f,
    )

    @Test
    fun paletteSurvivesRoundTrip() {
        val original = palette()
        val text = SkySnapshotStorage.encodePalette(original)
        val restored = SkySnapshotStorage.decodePalette(text)
        // Цвета хранятся по 8 бит на канал, поэтому сравниваем записанный вид: он должен совпасть.
        assertEquals(text, restored?.let(SkySnapshotStorage::encodePalette))
        assertEquals(original.darkInk, restored?.darkInk)
        assertEquals(original.smoke, restored?.smoke)
        assertEquals(original.ink.toArgb(), restored?.ink?.toArgb())
        assertEquals(original.infoBackdrop.toArgb(), restored?.infoBackdrop?.toArgb())
    }

    @Test
    fun snapshotsOfOldVersionsAreIgnored() {
        // Версии 1 и 2 (с подложками, шестнадцать и восемнадцать цветов) больше не читаются: палитра нейтральная до расчёта.
        val v3 = SkySnapshotStorage.encodePalette(palette()).split(';')
        val colors = v3[1].split(',')
        val sixteen = v3[1]
        val eighteen = (colors + listOf("0", "0")).joinToString(",")
        assertNull(SkySnapshotStorage.decodePalette("1;$sixteen;${v3[2]};${v3[3]}"))
        assertNull(SkySnapshotStorage.decodePalette("2;$eighteen;${v3[2]};${v3[3]}"))
        // Версия 3 с неверным числом цветов записана неверно.
        assertNull(SkySnapshotStorage.decodePalette("3;$eighteen;${v3[2]};${v3[3]}"))
        assertNull(SkySnapshotStorage.decodePalette("3;${colors.drop(1).joinToString(",")};${v3[2]};${v3[3]}"))
    }

    @Test
    fun neutralPaletteSurvivesRoundTrip() {
        val restored = SkySnapshotStorage.decodePalette(SkySnapshotStorage.encodePalette(PogodaPalette.Neutral))
        assertEquals(PogodaPalette.Neutral.darkInk, restored?.darkInk)
        assertEquals(PogodaPalette.Neutral.ink.toArgb(), restored?.ink?.toArgb())
    }

    @Test
    fun brokenTextGivesNothing() {
        assertNull(SkySnapshotStorage.decodePalette(""))
        assertNull(SkySnapshotStorage.decodePalette("2;a;true;0"))
        assertNull(SkySnapshotStorage.decodePalette("1;1,2,3;true;0"))
        assertNull(SkySnapshotStorage.decodePalette(SkySnapshotStorage.encodePalette(palette()).replace("true", "да").replace("false", "нет")))
        assertNull(SkySnapshotStorage.decodePalette(SkySnapshotStorage.encodePalette(palette()).dropLast(3) + "xyz"))
    }
}
