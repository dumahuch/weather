package io.github.dumahuch.pogodaapp.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import io.github.dumahuch.pogodaapp.sky.FrostComposer
import io.github.dumahuch.pogodaapp.sky.FrostSheet
import io.github.dumahuch.pogodaapp.sky.FrostTexture
import java.lang.ref.SoftReference

/** Картинки инея в assets/frost (сгенерировал Дима 14.11.2026): крупные узоры для карточек и мелкие для маленького стекла. */
private const val LARGE_COUNT = 6
private const val DENSE_COUNT = 6

/** Сколько готовых узоров держать для стекла разных размеров (пока памяти хватает). */
private const val SHEETS_KEPT = 8

/**
 * Картинки инея загружаются один раз на всё приложение при первом морозе и держатся, пока памяти хватает. Серые, в памяти
 * только яркость (около 13 МБ на все двенадцать).
 */
internal object FrostTextures {
    private var kept: SoftReference<Pair<List<FrostTexture>, List<FrostTexture>>>? = null

    @Synchronized
    fun load(assets: AssetManager): Pair<List<FrostTexture>, List<FrostTexture>> {
        kept?.get()?.let { return it }
        val large = (1..LARGE_COUNT).map { read(assets, "frost/large_${it.toString().padStart(2, '0')}.webp") }
        val dense = (1..DENSE_COUNT).map { read(assets, "frost/dense_${it.toString().padStart(2, '0')}.webp") }
        return (large to dense).also { kept = SoftReference(it) }
    }

    private fun read(assets: AssetManager, name: String): FrostTexture {
        val bitmap = assets.open(name).use { BitmapFactory.decodeStream(it) } ?: error("нет картинки $name")
        val size = minOf(bitmap.width, bitmap.height)
        val gray = ByteArray(size * size)
        val row = IntArray(size)
        for (y in 0 until size) {
            bitmap.getPixels(row, 0, size, 0, y, size, 1)
            for (x in 0 until size) gray[y * size + x] = (row[x] shr 16 and 0xFF).toByte()
        }
        bitmap.recycle()
        return FrostTexture(size, gray)
    }
}

/** Собранный иней для стекла одного размера: одна картинка, где красный это иней, зелёный порядок замерзания, синий дымка. */
internal object FrostSheets {
    private val kept = object : LinkedHashMap<Long, SoftReference<Bitmap>>(SHEETS_KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SoftReference<Bitmap>>?) = size > SHEETS_KEPT
    }

    @Synchronized
    fun cached(key: Long): Bitmap? = kept[key]?.get()

    /** Собирает иней (долго, не на главном потоке) и запоминает. */
    fun make(key: Long, width: Int, height: Int, pxPerDp: Double, assets: AssetManager, seed: Long): Bitmap {
        cached(key)?.let { return it }
        val (large, dense) = FrostTextures.load(assets)
        val bitmap = pack(FrostComposer.compose(width, height, pxPerDp, large, dense, seed))
        synchronized(this) { kept[key] = SoftReference(bitmap) }
        return bitmap
    }

    /** Непрозрачная картинка: каналы не портятся умножением на прозрачность. */
    private fun pack(sheet: FrostSheet): Bitmap {
        val pixels = IntArray(sheet.width * sheet.height) {
            val r = sheet.ice[it].toInt() and 0xFF
            val g = sheet.order[it].toInt() and 0xFF
            val b = sheet.haze[it].toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Иней рисует видеокарта: место замёрзло, когда доля инея [level] дошла до его порога (зелёный канал), край фронта мягкий.
 * След пальца берётся из маленькой картинки (прозрачность это сколько инея осталось). Вокруг инея лёгкая дымка.
 */
private const val FROST_AGSL = """
uniform shader frost;
uniform shader wipe;
uniform float level;
uniform float edge;
uniform float strength;
uniform float veil;
uniform float4 ice;

half4 main(float2 p) {
    half4 t = frost.eval(p);
    float g = t.g * (1.0 - edge);
    float grown = clamp((level - g) / edge, 0.0, 1.0);
    float near = clamp((level - g) / (edge * 4.0), 0.0, 1.0);
    float left = wipe.eval(p).a;
    float a = clamp(t.r * grown * strength + t.b * near * veil, 0.0, 1.0) * left;
    return half4(ice.rgb * a, a);
}
"""

/** Мягкость фронта (доля стекла), непрозрачность инея и дымки. */
private const val EDGE = 0.03f
private const val STRENGTH = 0.8f
private const val VEIL = 0.12f

internal class FrostLook {
    private val shader = RuntimeShader(FROST_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var frostBitmap: Bitmap? = null
    private var frostShader: BitmapShader? = null
    private var wipeBitmap: Bitmap? = null
    private var wipeShader: BitmapShader? = null

    fun draw(canvas: Canvas, width: Float, height: Float, frost: Bitmap, wipe: Bitmap, level: Float, appear: Float, color: Color) {
        val fs = frostShader.takeIf { frostBitmap === frost } ?: BitmapShader(frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
            it.filterMode = BitmapShader.FILTER_MODE_LINEAR
            frostBitmap = frost
            frostShader = it
        }
        val ws = wipeShader.takeIf { wipeBitmap === wipe } ?: BitmapShader(wipe, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
            it.filterMode = BitmapShader.FILTER_MODE_LINEAR
            wipeBitmap = wipe
            wipeShader = it
        }
        matrix.setScale(width / frost.width, height / frost.height)
        fs.setLocalMatrix(matrix)
        matrix.setScale(width / wipe.width, height / wipe.height)
        ws.setLocalMatrix(matrix)
        shader.setInputShader("frost", fs)
        shader.setInputShader("wipe", ws)
        shader.setFloatUniform("level", level)
        shader.setFloatUniform("edge", EDGE)
        shader.setFloatUniform("strength", STRENGTH * appear)
        shader.setFloatUniform("veil", VEIL * appear)
        shader.setFloatUniform("ice", color.red, color.green, color.blue, 1f)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
