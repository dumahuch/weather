package io.github.dumahuch.pogodaapp.ui.home

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import io.github.dumahuch.pogodaapp.home.CloudPlace
import io.github.dumahuch.pogodaapp.home.CloudShape
import io.github.dumahuch.pogodaapp.home.GlyphPart
import io.github.dumahuch.pogodaapp.home.GlyphScene
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Рисует значок погоды ([GlyphScene]) в картинку (step-42). Стекло: что лежит за облаком, видно сквозь него размытым,
 * снаружи чётким; облако молочное, с тонкой светлой кромкой и мягкой тенью. Контур: та же форма линией, что за облаком,
 * скрыто, вокруг облака зазор.
 *
 * Рисуется программно в [Bitmap], а не прямо на экран: размытие маской ([BlurMaskFilter]) аппаратная отрисовка Android
 * не поддерживает (таблица «Unsupported drawing operations», `setMaskFilter()`: developer.android.com/topic/performance/hardware-accel).
 * Картинка маленькая (значок около 72 dp) и рисуется заново, только когда меняется значок, цвета или размер.
 */
internal object GlyphPainter {

    fun render(scene: GlyphScene, style: WeatherIconStyle, colors: GlyphColors, sizePx: Int, outlineWidth: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx / VIEWPORT
        when (style) {
            WeatherIconStyle.Glass -> Glass(canvas, s, colors).draw(scene)
            WeatherIconStyle.Outline -> Outline(canvas, s, colors, outlineWidth).draw(scene)
        }
        return bitmap
    }

    private const val VIEWPORT = 24f

    /** Размытие того, что за стеклом, и тень облака (стандартное отклонение в единицах значка). */
    private const val BLUR_SIGMA = 1.5f
    private const val SHADOW_SIGMA = 1.1f
    private const val SHADOW_DROP = 1.3f
    private const val GLOW_SIGMA = 2.2f

    /** Общая часть: пути в пикселях (единицы значка × [s]) и кисти. */
    private abstract class Painter(val canvas: Canvas, val s: Float, val colors: GlyphColors) {
        private val matrix = Matrix().apply { setScale(s, s) }

        fun px(path: Path): Path = Path(path).apply { transform(matrix) }

        fun cloudPath(place: CloudPlace): Path {
            val units = Path().apply {
                val b = CloudShape.base
                addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW)
                for (c in CloudShape.circles) op(Path().apply { addCircle(c.x, c.y, c.r, Path.Direction.CW) }, Path.Op.UNION)
                transform(Matrix().apply { setScale(place.k, place.k); postTranslate(place.dx, place.dy) })
            }
            return px(units)
        }

        fun moonPath(m: GlyphPart.Moon): Path = px(
            Path().apply {
                addCircle(m.x, m.y, m.r, Path.Direction.CW)
                op(Path().apply { addCircle(m.cutX, m.cutY, m.cutR, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            },
        )

        fun boltPath(b: GlyphPart.Bolt): Path = px(
            Path().apply {
                b.points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                close()
            },
        )

        fun starPath(st: GlyphPart.Star): Path = Path().apply {
            for (k in 0 until 3) {
                val a = PI / 2 + k * PI / 3
                val dx = (cos(a) * st.r).toFloat()
                val dy = (sin(a) * st.r).toFloat()
                moveTo((st.x - dx) * s, (st.y - dy) * s)
                lineTo((st.x + dx) * s, (st.y + dy) * s)
            }
        }

        fun windPath(w: GlyphPart.Wind): Path = Path().apply {
            for (st in w.strokes) {
                moveTo(st.fromX * s, st.y * s)
                lineTo(st.toX * s, st.y * s)
                if (st.r > 0f) {
                    val oval = RectF((st.cx - st.r) * s, (st.cy - st.r) * s, (st.cx + st.r) * s, (st.cy + st.r) * s)
                    arcTo(oval, st.startDeg, st.sweepDeg, false)
                }
            }
        }

        fun raysPath(sun: GlyphPart.Sun): Path = Path().apply {
            for (k in 0 until 8) {
                val a = k * PI / 4
                val from = sun.r + GlyphPart.RAY_FROM
                val to = sun.r + GlyphPart.RAY_TO
                moveTo((sun.x + cos(a) * from).toFloat() * s, (sun.y + sin(a) * from).toFloat() * s)
                lineTo((sun.x + cos(a) * to).toFloat() * s, (sun.y + sin(a) * to).toFloat() * s)
            }
        }

        fun dashesPath(rain: GlyphPart.Rain): Path = Path().apply {
            for (d in rain.dashes) {
                moveTo(d.x1 * s, d.y1 * s)
                lineTo(d.x2 * s, d.y2 * s)
            }
        }

        /** Градиент сверху вниз (чуть наискосок, как свет на образцах) по рамке пути. */
        fun gradient(path: Path, pair: Pair<Int, Int>): Shader {
            val r = RectF().also { path.computeBounds(it, true) }
            return LinearGradient(r.left, r.top, r.left + r.width() * 0.35f, r.bottom, pair.first, pair.second, Shader.TileMode.CLAMP)
        }

        /** Вертикальный градиент штрихов, снега и ветра: от y 10 до y 23 значка, у всех частей одинаковый. */
        fun fall(pair: Pair<Int, Int>): Shader =
            LinearGradient(0f, 10f * s, 0f, 23f * s, pair.first, pair.second, Shader.TileMode.CLAMP)

        fun fill(shader: Shader? = null, color: Int = 0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (shader != null) this.shader = shader else this.color = color
        }

        fun line(width: Float, shader: Shader? = null, color: Int = 0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width * s
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (shader != null) this.shader = shader else this.color = color
        }

        /** Размытие маской: радиус Android переводит в стандартное отклонение как r × 0,57735 + 0,5. */
        fun Paint.blurred(sigmaUnits: Float): Paint = apply {
            val radius = max(0.5f, (sigmaUnits * s - 0.5f) / 0.57735f)
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }

        /** Слой, в котором части за облаком рисуются, а потом облако (с запасом [grow]) стирается поверх них. */
        inline fun behind(front: Path?, grow: Float, draw: () -> Unit) {
            val layer = canvas.saveLayer(null, null)
            draw()
            if (front != null) {
                val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR }
                canvas.drawPath(front, erase)
                if (grow > 0f) {
                    erase.style = Paint.Style.STROKE
                    erase.strokeWidth = grow * s
                    erase.strokeJoin = Paint.Join.ROUND
                    canvas.drawPath(front, erase)
                }
            }
            canvas.restoreToCount(layer)
        }
    }

    private class Glass(canvas: Canvas, s: Float, colors: GlyphColors) : Painter(canvas, s, colors) {

        fun draw(scene: GlyphScene) {
            val front = scene.front?.let(::cloudPath)
            if (front == null) {
                parts(scene, blur = 0f)
                return
            }
            val shadow = Path(front).apply { offset(0f, SHADOW_DROP * s) }
            canvas.drawPath(shadow, fill(color = colors.shadow).blurred(SHADOW_SIGMA))
            behind(front, 0f) { parts(scene, blur = 0f) }
            canvas.save()
            canvas.clipPath(front)
            parts(scene, blur = BLUR_SIGMA)
            canvas.restore()
            milk(front, colors.milk)
        }

        private fun milk(path: Path, milk: Pair<Int, Int>) {
            val r = RectF().also { path.computeBounds(it, true) }
            canvas.drawPath(path, fill(LinearGradient(0f, r.top, 0f, r.bottom, milk.first, milk.second, Shader.TileMode.CLAMP)))
            canvas.drawPath(path, line(RIM, color = colors.rim))
        }

        /** Части за облаком; [blur] больше нуля: размытые (то, что видно сквозь стекло). */
        private fun parts(scene: GlyphScene, blur: Float) {
            fun Paint.soft() = if (blur > 0f) blurred(blur) else this
            for (part in scene.parts) when (part) {
                is GlyphPart.Sun -> {
                    val disc = px(Path().apply { addCircle(part.x, part.y, part.r, Path.Direction.CW) })
                    if (part.rays) {
                        val glow = px(Path().apply { addCircle(part.x, part.y, part.r + 2f, Path.Direction.CW) })
                        canvas.drawPath(glow, fill(color = withAlpha(colors.sun.second, 0.35f)).blurred(GLOW_SIGMA))
                        canvas.drawPath(raysPath(part), line(GlyphPart.RAY_WIDTH, color = colors.sun.first).soft())
                    }
                    canvas.drawPath(disc, fill(gradient(disc, colors.sun)).soft())
                }
                is GlyphPart.Moon -> moonPath(part).let { canvas.drawPath(it, fill(gradient(it, colors.moon)).soft()) }
                is GlyphPart.BackCloud -> {
                    val back = cloudPath(part.place)
                    val r = RectF().also { back.computeBounds(it, true) }
                    val milk = LinearGradient(0f, r.top, 0f, r.bottom, colors.milkBack.first, colors.milkBack.second, Shader.TileMode.CLAMP)
                    canvas.drawPath(back, fill(milk).soft())
                    if (blur == 0f) canvas.drawPath(back, line(RIM, color = colors.rimBack))
                }
                is GlyphPart.Rain -> canvas.drawPath(dashesPath(part), line(GlyphPart.DASH_WIDTH, fall(colors.rain)).soft())
                is GlyphPart.Star -> canvas.drawPath(starPath(part), line(part.width, fall(colors.snow)).soft())
                is GlyphPart.Bolt -> boltPath(part).let {
                    val paint = fill(gradient(it, colors.bolt)).soft()
                    canvas.drawPath(it, paint)
                    canvas.drawPath(it, line(0.6f, gradient(it, colors.bolt)).soft())
                }
                is GlyphPart.Wind -> canvas.drawPath(windPath(part), line(GlyphPart.WIND_WIDTH, fall(colors.wind)).soft())
                is GlyphPart.Fog -> for (bar in part.bars) {
                    val h = GlyphPart.FOG_HEIGHT
                    val pill = px(Path().apply { addRoundRect(bar.x1 - 1f, bar.y1 - h / 2, bar.x2 + 1f, bar.y1 + h / 2, h / 2, h / 2, Path.Direction.CW) })
                    milk(pill, colors.milk)
                }
            }
        }

        companion object {
            const val RIM = 0.28f
        }
    }

    private class Outline(canvas: Canvas, s: Float, colors: GlyphColors, private val width: Float) : Painter(canvas, s, colors) {

        fun draw(scene: GlyphScene) {
            val front = scene.front?.let(::cloudPath)
            behind(front, width + 2 * GAP * width) { parts(scene) }
            if (front != null) canvas.drawPath(front, line(width, color = colors.ink))
        }

        private fun parts(scene: GlyphScene) {
            for (part in scene.parts) when (part) {
                is GlyphPart.Sun -> {
                    val disc = px(Path().apply { addCircle(part.x, part.y, if (part.rays) part.r - 0.6f else part.r, Path.Direction.CW) })
                    canvas.drawPath(disc, line(width, gradient(disc, colors.sun)))
                    if (part.rays) canvas.drawPath(raysPath(part), line(width, color = colors.sun.first))
                }
                is GlyphPart.Moon -> moonPath(part).let { canvas.drawPath(it, line(width, gradient(it, colors.moon))) }
                is GlyphPart.BackCloud -> canvas.drawPath(cloudPath(part.place), line(width, color = colors.ink))
                is GlyphPart.Rain -> canvas.drawPath(dashesPath(part), line(width, fall(colors.rain)))
                is GlyphPart.Star -> canvas.drawPath(starPath(part), line(width * part.width / GlyphPart.DASH_WIDTH, fall(colors.snow)))
                is GlyphPart.Bolt -> boltPath(part).let { canvas.drawPath(it, line(width * 0.9f, gradient(it, colors.bolt))) }
                is GlyphPart.Wind -> canvas.drawPath(windPath(part), line(width, fall(colors.wind)))
                is GlyphPart.Fog -> for (bar in part.bars) {
                    canvas.drawLine(bar.x1 * s, bar.y1 * s, bar.x2 * s, bar.y1 * s, line(width, color = colors.ink))
                }
            }
        }

        companion object {
            /** Зазор вокруг облака, в долях толщины линии. */
            const val GAP = 0.7f
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
}
