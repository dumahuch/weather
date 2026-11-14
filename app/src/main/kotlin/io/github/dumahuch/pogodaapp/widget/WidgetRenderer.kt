package io.github.dumahuch.pogodaapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Рисует виджет целиком в картинку ([Bitmap]) (значок слева, цифра справа в макете Димы от 12.11.2026, с 14.11.2026 небо «Ди»,
 * слайдер «Минимум / Обычно / Подробно» и размер 1×2). Картинкой, а не набором элементов Glance: тонкие цифры Manrope, стеклянные значки
 * и нарисованное небо приложения средствами виджета не показать, а картинка даёт ровно тот вид, что в макете. Всё рисуется в «единицах
 * макета» ([WidgetLayout.designWidth]) и масштабируется под настоящий размер; ширина и высота холста в этих единицах
 * могут быть больше номинальных. Фон (небо, стекло) рисует этот класс, разметку сведений [WidgetPage].
 */
internal class WidgetRenderer(private val context: Context) {

    fun render(model: WidgetModel?, layout: WidgetLayout, widthPx: Int, heightPx: Int, style: WidgetStyle, appIcons: WeatherIconStyle): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = min(widthPx / layout.designWidth, heightPx / layout.designHeight)
        canvas.scale(scale, scale)
        val w = widthPx / scale
        val h = heightPx / scale
        val sky = style.background == WidgetBackground.Sky && model != null
        if (style.background == WidgetBackground.Glass) drawGlass(canvas, w, h, style.opacity)
        if (sky && model != null) drawSky(canvas, w, h, scale, model, layout, style)
        // Без неба и без плотного стекла буквы лежат прямо на обоях: им нужна тень.
        val shadow = style.background == WidgetBackground.None || (style.background == WidgetBackground.Glass && style.opacity < SHADOW_BELOW_OPACITY)
        val d = WidgetDrawer(context, canvas, scale, shadow, style.icons ?: appIcons)
        if (model == null) {
            d.text(context.getString(R.string.widget_no_data), w / 2, h / 2 + 9f, d.pen(26f, 400, 0.9f), Paint.Align.CENTER)
            return bitmap
        }
        WidgetPage(context, d, model, w, h, sky, style.detail).draw(layout)
        return bitmap
    }

    /** Тёмное стекло цвета интерфейса с тонкой светлой кромкой; насколько оно непрозрачно, решает человек. */
    private fun drawGlass(canvas: Canvas, w: Float, h: Float, opacity: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((opacity * 255 / 100f).roundToInt(), GLASS_R, GLASS_G, GLASS_B)
        canvas.drawRoundRect(rect, CORNER, CORNER, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(RIM_ALPHA, 255, 255, 255)
        canvas.drawRoundRect(rect, CORNER, CORNER, paint)
    }

    /** Небо «Ди» в скруглённых углах, вуаль под текстом по [WidgetStyle.shade] и тонкая светлая кромка. */
    private fun drawSky(canvas: Canvas, w: Float, h: Float, unit: Float, model: WidgetModel, layout: WidgetLayout, style: WidgetStyle) {
        val rect = RectF(0f, 0f, w, h)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, CORNER, CORNER, Path.Direction.CW) })
        WidgetSky(context).draw(canvas, w, h, unit, model)
        drawScrim(canvas, w, h, style.shade / SHADE_REFERENCE, layout, style.detail)
        canvas.restore()
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(SKY_RIM_ALPHA, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), CORNER, CORNER, rim)
    }

    /**
     * Вуаль под белым текстом, чтобы цифры читались на любом небе: темнее вверху и внизу, где текст, и у левого края. [k] сила
     * (1 это эталон макета). В «Подробно» 4×2 справа ещё колонка дней, в 4×4 внизу пять дней: там вуаль плотнее.
     */
    private fun drawScrim(canvas: Canvas, w: Float, h: Float, k: Float, layout: WidgetLayout, detail: WidgetDetail) {
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(ink(.40f * k), ink(.04f * k), ink(.08f * k), ink(.52f * k)),
            floatArrayOf(0f, .38f, .62f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(ink(.30f * k), ink(.06f * k), ink(0f)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        if (detail != WidgetDetail.Full) return
        when (layout) {
            WidgetLayout.Wide -> {
                val x = w - WidgetPage.WIDE_DAYS_WIDTH - DAYS_VEIL_REACH
                paint.shader = LinearGradient(x, 0f, w, 0f, ink(0f), ink(.55f * k), Shader.TileMode.CLAMP)
                canvas.drawRect(x, 0f, w, h, paint)
            }
            WidgetLayout.Large -> {
                val y = h - WidgetPage.LARGE_DAYS_HEIGHT - DAYS_VEIL_REACH
                paint.shader = LinearGradient(0f, y, 0f, h, intArrayOf(ink(0f), ink(.45f * k), ink(.6f * k)), floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, y, w, h, paint)
            }
            else -> Unit
        }
    }

    private fun ink(alpha: Float): Int = Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), SCRIM_R, SCRIM_G, SCRIM_B)

    private companion object {
        const val CORNER = 40f
        const val GLASS_R = 20
        const val GLASS_G = 40
        const val GLASS_B = 70
        const val SCRIM_R = 10
        const val SCRIM_G = 20
        const val SCRIM_B = 34
        const val RIM_ALPHA = 87
        const val SKY_RIM_ALPHA = 46
        const val SHADOW_BELOW_OPACITY = 50

        /** Затемнение в процентах, при котором вуаль такая же, как в макете. */
        const val SHADE_REFERENCE = 40f
        const val DAYS_VEIL_REACH = 160f
    }
}
