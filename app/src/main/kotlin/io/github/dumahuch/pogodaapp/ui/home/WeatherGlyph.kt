package io.github.dumahuch.pogodaapp.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.dumahuch.pogodaapp.home.GlyphScenes
import io.github.dumahuch.pogodaapp.home.HeroIcon
import io.github.dumahuch.pogodaapp.home.heroIconFor
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.weather.Condition
import kotlin.math.roundToInt

/** Выбранный набор значков погоды (настройка «Значки погоды»), задаётся в `MainActivity`. */
val LocalWeatherIconStyle = staticCompositionLocalOf { WeatherIconStyle.Glass }

/** Вид погоды, подставленный экраном проверки; `null`: значок у температуры берёт настоящую погоду. Задаётся в `MainActivity`. */
val LocalHeroConditionOverride = staticCompositionLocalOf<Condition?> { null }

/** Солнечный час (0…24), подставленный экраном проверки; `null`: день или ночь берутся из настоящих данных. */
val LocalHeroSolarHourOverride = staticCompositionLocalOf<Double?> { null }

/** Сильный ветер, подставленный экраном проверки; `null`: по настоящему ветру. */
val LocalHeroWindOverride = staticCompositionLocalOf<Boolean?> { null }

/** Экран проверки: гроза с дождём для значка у температуры (пусто: по настоящим осадкам). */
val LocalHeroWetOverride = staticCompositionLocalOf<Boolean?> { null }

/** Толщина контура в единицах значка: тоньше на крупном значке, толще на мелком (подбирается по размеру, не в настройках). */
internal fun outlineStroke(size: Dp): Float = if (size.value >= 40f) 1.4f else 1.7f

/**
 * Значок погоды «Главной» в выбранном наборе ([WeatherIconStyle]), квадрат [size]. [night]: ночное небо, стекло темнее
 * и прозрачнее. Рисуется картинкой ([GlyphPainter]), заново только при смене значка, набора, цветов или размера.
 */
@Composable
fun WeatherGlyph(icon: HeroIcon, style: WeatherIconStyle, size: Dp, night: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val ink = palette.ink.toArgb()
    val tone = when {
        palette.darkInk -> GlyphTone.Light
        night -> GlyphTone.Night
        else -> GlyphTone.Day
    }
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val stroke = outlineStroke(size)
    val bitmap = remember(icon, style, tone, ink, px, stroke) {
        GlyphPainter.render(GlyphScenes.of(icon), style, GlyphColors.of(tone, ink), px, stroke).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Мелкий значок погоды для ленты, «Прогноза» и подробностей дня (решение Димы 08.11.2026: тот же набор, что на
 * «Главной», для единого стиля). Всегда контурный, стекло в мелком размере отпало (решение 07.11.2026). [wet]: у
 * грозы идут осадки ([io.github.dumahuch.pogodaapp.home.isStormWet]). Ясно: солнце с лучами или месяц.
 */
@Composable
fun SmallWeatherGlyph(condition: Condition, isDay: Boolean, wet: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val ink = palette.ink.toArgb()
    val tone = if (palette.darkInk) GlyphTone.Light else GlyphTone.Day
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val stroke = outlineStroke(size)
    val bitmap = remember(condition, isDay, wet, tone, ink, px, stroke) {
        GlyphPainter.render(
            GlyphScenes.small(condition, isDay, wet), WeatherIconStyle.Outline, GlyphColors.of(tone, ink, small = true), px, stroke,
        ).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Значок погоды картинкой [sizePx] × [sizePx] для уведомления (0.7.0): тот же набор, что на «Главной». [onLight]: фон
 * шторки светлый (дневная тема системы), линии и стекло темнее. При ясной погоде неба за значком нет, поэтому вместо
 * пустого места солнце днём и месяц ночью (при сильном ветре ветер).
 */
fun weatherGlyphBitmap(condition: Condition, isDay: Boolean, windy: Boolean, style: WeatherIconStyle, onLight: Boolean, sizePx: Int): Bitmap {
    val icon = heroIconFor(condition, isDay, windy)
    val scene = if (icon == null) GlyphScenes.clear(isDay) else GlyphScenes.of(icon)
    val colors = if (onLight) GlyphColors.of(GlyphTone.Light, NOTIFY_INK_LIGHT) else GlyphColors.of(GlyphTone.Night, NOTIFY_INK_DARK)
    return GlyphPainter.render(scene, style, colors, sizePx, NOTIFY_STROKE)
}

/** Цвет линий контурного значка в шторке: как текст уведомлений Android на светлом и тёмном фоне. */
private val NOTIFY_INK_LIGHT = 0xFF1F1F1F.toInt()
private val NOTIFY_INK_DARK = 0xFFE3E3E3.toInt()

/** Значок уведомления мелкий (около 40 dp): линия толще, чем у крупного значка «Главной». */
private const val NOTIFY_STROKE = 1.7f

/** Фон под значком: дневное небо (светлый текст), ночное небо, светлый фон (тёмный текст или светлая шторка). */
internal enum class GlyphTone { Day, Night, Light }

/**
 * Цвета значка. Пары: верх и низ градиента. Солнце жёлто-оранжевое, луна сине-фиолетовая, молния жёлтая (решение Димы
 * 07.11.2026), дождь и снег синие, ветер бирюзово-синий; облако молочное ([milk], у заднего облака [milkBack]).
 * [ink]: линия контурного набора, цвет текста.
 */
internal class GlyphColors(
    val sun: Pair<Int, Int>,
    val moon: Pair<Int, Int>,
    val bolt: Pair<Int, Int>,
    val rain: Pair<Int, Int>,
    val snow: Pair<Int, Int>,
    val wind: Pair<Int, Int>,
    val milk: Pair<Int, Int>,
    val milkBack: Pair<Int, Int>,
    val shadow: Int,
    val rim: Int,
    val rimBack: Int,
    val ink: Int,
) {
    companion object {
        private fun rgb(v: Long) = v.toInt()
        private fun argb(rgb: Long, alpha: Float) = ((alpha * 255).roundToInt() shl 24) or (rgb.toInt() and 0xFFFFFF)

        private val SUN = rgb(0xFFFFD84A) to rgb(0xFFFF8A1C)
        private val BOLT = rgb(0xFFFFE45A) to rgb(0xFFFFAE00)

        /** Солнце и молния темнее для мелких значков на светлом небе: светло-жёлтая тонкая линия там почти пропадает. */
        private val SUN_DEEP = rgb(0xFFE08A00) to rgb(0xFFB85000)
        private val BOLT_DEEP = rgb(0xFFE8A800) to rgb(0xFFC27000)
        private const val WHITE = 0xFFFFFFL
        private const val MILK_LOW = 0xE2EBF8L
        private const val SHADOW = 0x2B4F8AL

        fun of(tone: GlyphTone, ink: Int, small: Boolean = false): GlyphColors = when (tone) {
            GlyphTone.Day -> GlyphColors(
                sun = SUN,
                moon = rgb(0xFF4B7DFF) to rgb(0xFF9A4DFF),
                bolt = BOLT,
                rain = rgb(0xFF2A6CF0) to rgb(0xFF3FB6FF),
                snow = rgb(0xFF2A7AF0) to rgb(0xFF57C4FF),
                wind = rgb(0xFF33C7CF) to rgb(0xFF2F7DF0),
                milk = argb(WHITE, 0.80f) to argb(MILK_LOW, 0.62f),
                milkBack = argb(WHITE, 0.62f) to argb(MILK_LOW, 0.45f),
                shadow = argb(SHADOW, 0.26f),
                rim = argb(WHITE, 0.75f),
                rimBack = argb(WHITE, 0.55f),
                ink = ink,
            )
            GlyphTone.Night -> GlyphColors(
                sun = SUN,
                moon = rgb(0xFF5A8CFF) to rgb(0xFFA35CFF),
                bolt = BOLT,
                rain = rgb(0xFF3D86FF) to rgb(0xFF62CCFF),
                snow = rgb(0xFF4A8CFF) to rgb(0xFF7FD4FF),
                wind = rgb(0xFF3FD3D6) to rgb(0xFF3F8CFF),
                milk = argb(WHITE, 0.42f) to argb(MILK_LOW, 0.26f),
                milkBack = argb(WHITE, 0.32f) to argb(MILK_LOW, 0.20f),
                shadow = argb(SHADOW, 0.45f),
                rim = argb(WHITE, 0.75f),
                rimBack = argb(WHITE, 0.50f),
                ink = ink,
            )
            GlyphTone.Light -> GlyphColors(
                sun = if (small) SUN_DEEP else SUN,
                moon = rgb(0xFF4B7DFF) to rgb(0xFF9A4DFF),
                bolt = if (small) BOLT_DEEP else BOLT,
                rain = rgb(0xFF2A6CF0) to rgb(0xFF3FB6FF),
                snow = rgb(0xFF2A7AF0) to rgb(0xFF57C4FF),
                wind = rgb(0xFF22AEB8) to rgb(0xFF2F7DF0),
                milk = argb(0xF4F7FBL, 0.95f) to argb(0xC9D5E4L, 0.90f),
                milkBack = argb(0xEEF2F8L, 0.90f) to argb(0xD5DDE8L, 0.85f),
                shadow = argb(SHADOW, 0.22f),
                rim = argb(0x2A3B52L, 0.22f),
                rimBack = argb(0x2A3B52L, 0.18f),
                ink = ink,
            )
        }
    }
}
