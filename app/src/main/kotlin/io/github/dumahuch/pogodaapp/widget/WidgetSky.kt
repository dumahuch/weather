package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import io.github.dumahuch.pogodaapp.sky.Celestial
import io.github.dumahuch.pogodaapp.sky.CloudLayout
import io.github.dumahuch.pogodaapp.sky.DiArt
import io.github.dumahuch.pogodaapp.sky.Rgb
import io.github.dumahuch.pogodaapp.sky.SkyDrift
import io.github.dumahuch.pogodaapp.sky.SkyLook
import io.github.dumahuch.pogodaapp.sky.SkySampler
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import io.github.dumahuch.pogodaapp.sky.SunPosition
import io.github.dumahuch.pogodaapp.weather.Condition
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Небо «Ди» для фона виджета (решение Димы 14.11.2026): те же слои, облака, солнце и луна, та же погода (серость, вуаль,
 * затемнение грозы, ночное приглушение), что на главном экране ([io.github.dumahuch.pogodaapp.ui.sky.DiPainter]), в том же порядке:
 * небо → солнце и луна → облака → пять гряд. Рисуется один раз на картинку виджета, без движения; слои читаются с диска по одному
 * и сразу выбрасываются, а крупные картинки уменьшаются при чтении (виджету не нужны 1024×1536 пикселей). Дождь и снег
 * отмечены неподвижными штрихами и точками.
 * Окно виджета берёт из картины пояс гряд ([DiArt.placeWidget]); солнце и луна, что лежат выше окна, прижаты к его верхнему краю.
 */
internal class WidgetSky(private val context: Context, private val screen: Boolean = false) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Рисует небо на весь холст [w]×[h] (единицы макета); [unit] сколько пикселей холста в одной единице (от него зависит, как сильно уменьшать картинки). */
    fun draw(canvas: Canvas, w: Float, h: Float, unit: Float, model: WidgetModel) {
        val state = widgetSkyState(model)
        val look = state.look(SkyDrift.position(state.pan, SkyDrift.START_PHASE))
        val scenes = look.weights.visible()
        if (scenes.isEmpty()) return
        val pan = DiArt.pan(look.weights)
        val bounds = RectF(0f, 0f, w, h)

        // Небо с вуалью под серостью, затемнением и приглушением; солнце и луна лежат на небе своим цветом.
        withGray(canvas, look, bounds, force = false) {
            drawLayers(canvas, look, scenes, listOf(DiArt.Layer.Sky), w, h, unit, pan)
            drawVeil(canvas, look, w, h, PorterDuff.Mode.SRC_OVER)
        }
        drawTone(canvas, look, bounds, PorterDuff.Mode.SRC_OVER)
        drawCelestial(canvas, look, w, h, unit)

        // Облака и гряды в своём слое: вуаль, серость и затемнение красят только их.
        val tone = look.weather.dark > MIN_EFFECT || look.nightDim > MIN_EFFECT
        if (tone) canvas.saveLayer(bounds, null)
        withGray(canvas, look, bounds, force = look.veilStrength > MIN_EFFECT) {
            drawClouds(canvas, look, w, h, unit)
            drawVeil(canvas, look, w, h, PorterDuff.Mode.SRC_ATOP)
            drawLayers(canvas, look, scenes, DiArt.Layer.entries.filter { it != DiArt.Layer.Sky }, w, h, unit, pan)
        }
        if (tone) {
            drawTone(canvas, look, bounds, PorterDuff.Mode.SRC_ATOP)
            canvas.restore()
        }
        if (!screen) drawPrecipitation(canvas, w, h, model)
    }

    /** Окно виджета смотрит на пояс гряд; снимок для экрана ([screen]) кладёт картину так же, как главный экран. */
    private fun placeOf(layer: DiArt.Layer, w: Float, h: Float, pan: Double): Placement =
        if (screen) DiArt.place(layer, w.toDouble(), h.toDouble(), pan, 0.0) else DiArt.placeWidget(layer, w.toDouble(), h.toDouble(), pan)

    /** Слои [layers] каждого времени суток из [scenes], смешанные каскадом (вес / сумма весов до него), как на экране. */
    private fun drawLayers(canvas: Canvas, look: SkyLook, scenes: List<SkyScene>, layers: List<DiArt.Layer>, w: Float, h: Float, unit: Float, pan: Double) {
        for (layer in layers) {
            val place = placeOf(layer, w, h, pan)
            var sum = 0.0
            for (scene in scenes) {
                val weight = look.weights[scene]
                sum += weight
                val sample = sampleFor(place.scale * unit)
                val bitmap = decode("sky/di/${DiArt.file(scene, layer)}.webp", sample) ?: continue
                paint.colorFilter = null
                paint.alpha = ((weight / sum) * 255).roundToInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(place.left.toFloat(), place.top.toFloat())
                val scale = (place.scale * sample).toFloat()
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                canvas.restore()
                bitmap.recycle()
            }
        }
    }

    /** Облака погоды: число и размер по густоте, подкраска под время суток, как [io.github.dumahuch.pogodaapp.ui.sky.DiPainter]. */
    private fun drawClouds(canvas: Canvas, look: SkyLook, w: Float, h: Float, unit: Float) {
        val list = CloudLayout.of(look.weather)
        val place = DiArt.placeClouds(w.toDouble(), h.toDouble(), look.cloudPosition)
        val sample = sampleFor(place.scale * unit * CLOUD_MEAN_SCALE)
        val sheets = HashMap<Boolean, Bitmap?>()
        paint.colorFilter = tint(look)
        for (cloud in list) {
            val left = place.left + cloud.left * place.scale
            val top = place.top + cloud.top * place.scale
            val width = cloud.width * place.scale
            val height = cloud.height * place.scale
            if (left + width < 0 || left > w || top + height < 0 || top > h) continue
            val sheet = sheets.getOrPut(cloud.storm) { decode(if (cloud.storm) "sky/di/clouds_storm.webp" else "sky/di/clouds_light.webp", sample) } ?: continue
            val source = Rect(cloud.rect.x / sample, cloud.rect.y / sample, (cloud.rect.x + cloud.rect.w) / sample, (cloud.rect.y + cloud.rect.h) / sample)
            val scale = (cloud.scale * place.scale).toFloat()
            paint.alpha = (cloud.alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.save()
            if (cloud.mirrored) {
                canvas.translate((left + width).toFloat(), top.toFloat())
                canvas.scale(-scale, scale)
            } else {
                canvas.translate(left.toFloat(), top.toFloat())
                canvas.scale(scale, scale)
            }
            canvas.drawBitmap(sheet, source, RectF(0f, 0f, cloud.rect.w.toFloat(), cloud.rect.h.toFloat()), paint)
            canvas.restore()
        }
        paint.colorFilter = null
        sheets.values.forEach { it?.recycle() }
    }

    /**
     * Солнце и луна тем же расчётом, что на экране ([Celestial.discs]), но размер по меньшей стороне окна, а не по ширине (иначе
     * в широком виджете диск был бы больше окна). Высота пересчитана на пояс картины, выше окна диск прижат к верхнему краю.
     */
    private fun drawCelestial(canvas: Canvas, look: SkyLook, w: Float, h: Float, unit: Float) {
        if (look.bodies.isEmpty()) return
        val ref = min(w, h).toDouble()
        val referenceScale = DiArt.place(DiArt.Layer.Sky, ref, h.toDouble(), 0.5, 0.0).scale
        val place = placeOf(DiArt.Layer.Sky, w, h, 0.5)
        for (disc in Celestial.discs(look.bodies, ref, h.toDouble())) {
            val body = disc.body
            val side = disc.imageSize.toFloat()
            val cx = (disc.cx * w / ref).toFloat()
            val canvasY = place.top + disc.cy / referenceScale * place.scale
            val cy = max(canvasY, disc.radius * TOP_CLAMP).toFloat()
            val bitmap = decode("sky/celestial/${body.file}.webp", sampleFor(side / BITMAP_SIDE * unit)) ?: continue
            if (body.moon && body.lit < 1.0) {
                val radius = (disc.radius * MOON_GLOW_RADIUS).toFloat()
                fillPaint.shader = RadialGradient(
                    cx, cy, radius,
                    Color.argb((MOON_GLOW_ALPHA * body.alpha * 255).roundToInt(), 205, 222, 255), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.shader = null
            }
            paint.colorFilter = null
            paint.alpha = (body.alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.save()
            if (body.mirrored) canvas.scale(-1f, 1f, cx, cy)
            canvas.drawBitmap(bitmap, null, RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2), paint)
            canvas.restore()
            bitmap.recycle()
        }
    }

    /** Рисует [body] на слое, где цвет тянется к серому непогодой (если она заметна или слой нужен для [PorterDuff.Mode.SRC_ATOP]). */
    private inline fun withGray(canvas: Canvas, look: SkyLook, bounds: RectF, force: Boolean, body: () -> Unit) {
        val gray = look.grayAmount
        val separate = gray > MIN_EFFECT || force
        if (separate) {
            val layerPaint = Paint()
            if (gray > MIN_EFFECT) layerPaint.colorFilter = grayFilter(gray)
            canvas.saveLayer(bounds, layerPaint)
        }
        body()
        if (separate) canvas.restore()
    }

    private fun grayFilter(amount: Double): ColorMatrixColorFilter {
        val keep = (1 - amount).toFloat()
        val a = amount.toFloat()
        val r = 0.30f * a
        val g = 0.59f * a
        val b = 0.11f * a
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    keep + r, g, b, 0f, 0f,
                    r, keep + g, b, 0f, 0f,
                    r, g, keep + b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    /** Затемнение грозы и приглушение света ночью. */
    private fun drawTone(canvas: Canvas, look: SkyLook, bounds: RectF, mode: PorterDuff.Mode) {
        fillPaint.xfermode = PorterDuffXfermode(mode)
        if (look.weather.dark > MIN_EFFECT) {
            fillPaint.color = Color.argb((look.darkAlpha * 255).roundToInt(), 10, 16, 32)
            canvas.drawRect(bounds, fillPaint)
        }
        if (look.nightDim > MIN_EFFECT) {
            fillPaint.color = Color.argb((look.nightDim * 255).roundToInt(), 8, 12, 26)
            canvas.drawRect(bounds, fillPaint)
        }
        fillPaint.xfermode = null
    }

    /** Погодная вуаль сверху вниз тремя цветами; [mode] SRC_ATOP красит только уже нарисованное (облака). */
    private fun drawVeil(canvas: Canvas, look: SkyLook, w: Float, h: Float, mode: PorterDuff.Mode) {
        val strength = look.veilStrength
        if (strength <= MIN_EFFECT) return
        val alpha = (strength * 255).roundToInt()
        val veil = look.veil
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(color(veil.top, alpha), color(veil.mid, alpha), color(veil.hor, alpha)),
            floatArrayOf(0f, SkySampler.VEIL_MID.toFloat(), 1f),
            Shader.TileMode.CLAMP,
        )
        fillPaint.xfermode = PorterDuffXfermode(mode)
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.xfermode = null
        fillPaint.shader = null
    }

    private fun color(rgb: Rgb, alpha: Int): Int =
        Color.argb(alpha, rgb.r.roundToInt().coerceIn(0, 255), rgb.g.roundToInt().coerceIn(0, 255), rgb.b.roundToInt().coerceIn(0, 255))

    /** Подкраска облаков: цвет времени суток, серость плотной облачности и подъём яркости при малой (результат обрезается белым). */
    private fun tint(look: SkyLook): ColorMatrixColorFilter {
        val t = DiArt.tint(look.weights)
        val k = look.weather.cloudShade * look.weather.cloudLift
        val scale = 1f / 255f
        val r = (t.r * k).toFloat().coerceIn(0f, MAX_TINT) * scale
        val g = (t.g * k).toFloat().coerceIn(0f, MAX_TINT) * scale
        val b = (t.b * k).toFloat().coerceIn(0f, MAX_TINT) * scale
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r, 0f, 0f, 0f, 0f,
                    0f, g, 0f, 0f, 0f,
                    0f, 0f, b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    /** Дождь и снег неподвижными штрихами и точками (одно и то же место при каждой отрисовке, без случайных чисел). */
    private fun drawPrecipitation(canvas: Canvas, w: Float, h: Float, model: WidgetModel) {
        val snow = model.condition == Condition.Snow
        val rain = model.condition == Condition.Rain || model.condition == Condition.Storm
        if (!snow && !rain) return
        val count = (if (snow) SNOW_FLAKES else if (model.drizzle) RAIN_STREAKS / 2 else RAIN_STREAKS) * (w * h / (REFERENCE * REFERENCE)).coerceIn(0.4f, 4f)
        fillPaint.color = Color.argb(if (snow) 150 else 80, 255, 255, 255)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = if (snow) 0f else 2f
        fillPaint.strokeCap = Paint.Cap.ROUND
        var seed = 12345L
        for (i in 0 until count.toInt()) {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val x = (seed % 10_000) / 10_000f * w
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val y = (seed % 10_000) / 10_000f * h
            if (snow) {
                fillPaint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 2.5f + (i % 3), fillPaint)
            } else {
                canvas.drawLine(x, y, x - RAIN_SLANT, y + RAIN_LENGTH, fillPaint)
            }
        }
        fillPaint.style = Paint.Style.FILL
        fillPaint.strokeWidth = 0f
    }

    /** Наибольшая степень двойки, при которой картинка после уменьшения всё ещё не мельче, чем рисуется ([scale] пикселей холста на пиксель картинки). */
    private fun sampleFor(scale: Double): Int {
        var sample = 1
        while (sample < MAX_SAMPLE && scale * sample * 2 <= 1.0) sample *= 2
        return sample
    }

    private fun decode(path: String, sample: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.assets, path)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sample)
        }
    } catch (e: IOException) {
        Log.w(TAG, "Не удалось прочитать картинку неба $path", e)
        null
    }

    private companion object {
        const val TAG = "PogodaWidget"
        const val MIN_EFFECT = 0.01
        const val MAX_TINT = 400f
        const val MAX_SAMPLE = 4

        /** Облака на листе крупнее половины листа в среднем: для выбора уменьшения берётся их средний масштаб. */
        const val CLOUD_MEAN_SCALE = 0.8

        /** Картинки солнца и луны квадратные, 1254 пикселя (по ним выбирается уменьшение). */
        const val BITMAP_SIDE = 1254.0

        /** Диск выше окна прижимается так, чтобы был виден на этой доле радиуса от верха. */
        const val TOP_CLAMP = 0.9
        const val MOON_GLOW_RADIUS = 1.5
        const val MOON_GLOW_ALPHA = 0.28
        const val RAIN_STREAKS = 70
        const val SNOW_FLAKES = 60
        const val RAIN_LENGTH = 26f
        const val RAIN_SLANT = 8f
        const val REFERENCE = 444f
    }
}

/** Небо по модели виджета: то же, что строит главный экран по прогнозу (солнце, луна, погода, облачность). */
internal fun widgetSkyState(model: WidgetModel): SkyState {
    val latitude = model.latitude
    return SkyState.of(
        SunPosition.at(latitude, model.longitude, model.now),
        model.condition,
        model.hourPrecipitationMm,
        model.cloudCoverPercent,
        Celestial.moonSky(latitude, model.longitude, model.now),
        SunPosition.atSolarHour(latitude, model.now, 12.0).elevation,
    )
}
