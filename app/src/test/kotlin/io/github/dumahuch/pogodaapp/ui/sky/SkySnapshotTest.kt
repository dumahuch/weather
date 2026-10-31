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
        scrim = Color(0x66102030),
        infoScrim = Color(0x40102030),
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
        assertEquals(original.scrim.toArgb(), restored?.scrim?.toArgb())
        assertEquals(original.infoScrim.toArgb(), restored?.infoScrim?.toArgb())
        assertEquals(original.infoBackdrop.toArgb(), restored?.infoBackdrop?.toArgb())
    }

    @Test
    fun oldSnapshotWithSixteenColorsStillLoads() {
        // Снимок, записанный версиями до 0.5.3: шестнадцать цветов, подложки под строками нет.
        val v2 = SkySnapshotStorage.encodePalette(palette()).split(';')
        val sixteen = v2[1].split(',').take(16).joinToString(",")
        val restored = SkySnapshotStorage.decodePalette("1;$sixteen;${v2[2]};${v2[3]}")
        assertEquals(palette().ink.toArgb(), restored?.ink?.toArgb())
        assertEquals(Color.Transparent, restored?.infoScrim)
        assertEquals(PogodaPalette.Neutral.infoBackdrop, restored?.infoBackdrop)
        // Версия 2 с шестнадцатью цветами и версия 1 с восемнадцатью записаны неверно.
        assertNull(SkySnapshotStorage.decodePalette("2;$sixteen;${v2[2]};${v2[3]}"))
        assertNull(SkySnapshotStorage.decodePalette("1;${v2[1]};${v2[2]};${v2[3]}"))
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
