package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import io.github.dumahuch.pogodaapp.ui.PogodaPalette
import java.io.File
import java.io.IOException

/** Последнее небо, которое видел пользователь: маленькая картинка и цвета интерфейса к ней. */
internal class SkySnapshot(val image: ImageBitmap, val palette: PogodaPalette)

/**
 * Хранит снимок последнего неба в папке приложения: уменьшенную картинку (WebP, несколько килобайт) и рядом
 * цвета интерфейса текстом. При запуске приложение сразу показывает этот снимок, пока настоящие картины
 * декодируются, и небо проявляется поверх плавно: нет голубого мигания.
 * Ошибки чтения и записи не страшны: снимка просто не будет.
 */
internal object SkySnapshotStorage {

    private const val IMAGE_FILE = "sky_snapshot.webp"
    private const val PALETTE_FILE = "sky_snapshot.txt"
    private const val TAG = "PogodaSky"

    /** Читает снимок; читается на главном потоке при запуске, файл крошечный. */
    fun load(context: Context): SkySnapshot? = try {
        val palette = decodePalette(File(context.filesDir, PALETTE_FILE).readText())
        val bitmap = BitmapFactory.decodeFile(File(context.filesDir, IMAGE_FILE).path)
        if (palette == null || bitmap == null) null else SkySnapshot(bitmap.asImageBitmap(), palette)
    } catch (e: IOException) {
        null
    }

    /** Записывает снимок (блокирующий вызов, вызывать не с главного потока). */
    fun save(context: Context, bitmap: Bitmap, palette: PogodaPalette) {
        try {
            val image = File(context.filesDir, IMAGE_FILE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, it) }
            File(context.filesDir, PALETTE_FILE).writeText(encodePalette(palette))
        } catch (e: IOException) {
            Log.w(TAG, "Не удалось сохранить снимок неба", e)
        }
    }

    private const val WEBP_QUALITY = 80

    /**
     * Палитра одной строкой: версия 2; восемнадцать цветов ARGB (16-ричные); тёмный текст; дымчатость.
     * Версия 1 (шестнадцать цветов, без подложки под строками под температурой) читается как прежде.
     */
    fun encodePalette(palette: PogodaPalette): String {
        val colors = colorsOf(palette).joinToString(",") { Integer.toHexString(it.toArgb()) }
        return "2;$colors;${palette.darkInk};${palette.smoke}"
    }

    /** Обратное к [encodePalette]; любая неисправность даёт пусто. */
    fun decodePalette(text: String): PogodaPalette? {
        val parts = text.trim().split(';')
        if (parts.size != 4) return null
        val expected = when (parts[0]) {
            "1" -> COLOR_COUNT_V1
            "2" -> COLOR_COUNT
            else -> return null
        }
        val colors = parts[1].split(',').map { Color(it.toLongOrNull(16)?.toInt() ?: return null) }
        if (colors.size != expected) return null
        val dark = parts[2].toBooleanStrictOrNull() ?: return null
        val smoke = parts[3].toFloatOrNull() ?: return null
        return PogodaPalette(
            ink = colors[0],
            ink2 = colors[1],
            glass = colors[2],
            glassLine = colors[3],
            glassHighlight = colors[4],
            buttonHighlight = colors[5],
            shadow = colors[6],
            sheen = colors[7],
            buttonBackground = colors[8],
            buttonOn = colors[9],
            button2 = colors[10],
            accent = colors[11],
            accentSoft = colors[12],
            bar = colors[13],
            warning = colors[14],
            scrim = colors[15],
            // Старый снимок: подложки под строками нет, цвет неба под ними нейтральный (до первого настоящего расчёта).
            infoScrim = colors.getOrNull(16) ?: Color.Transparent,
            infoBackdrop = colors.getOrNull(17) ?: PogodaPalette.Neutral.infoBackdrop,
            darkInk = dark,
            smoke = smoke,
        )
    }

    private const val COLOR_COUNT_V1 = 16
    private const val COLOR_COUNT = 18

    private fun colorsOf(p: PogodaPalette) = listOf(
        p.ink, p.ink2, p.glass, p.glassLine, p.glassHighlight, p.buttonHighlight, p.shadow, p.sheen,
        p.buttonBackground, p.buttonOn, p.button2, p.accent, p.accentSoft, p.bar, p.warning, p.scrim,
        p.infoScrim, p.infoBackdrop,
    )
}
