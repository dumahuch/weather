package io.github.dumahuch.pogodaapp.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Цвета интерфейса в данный момент. Пока неба нет (загрузка, нет разрешения) это спокойный дневной вид
 * шага 1 ([Neutral]); с небом цвета считаются по картине и сменяются плавно: тёмный текст на светлом небе,
 * светлый на тёмном, плотность стекла ровно такая, чтобы мелкий текст читался.
 */
@Immutable
data class PogodaPalette(
    val ink: Color,
    val ink2: Color,
    val glass: Color,
    val glassLine: Color,
    /** Светлая линия по верхней кромке карточки. */
    val glassHighlight: Color,
    /** То же для кнопок и нижней панели. */
    val buttonHighlight: Color,
    /** Тень под кнопками и нижней панелью. */
    val shadow: Color,
    val sheen: Color,
    val buttonBackground: Color,
    val buttonOn: Color,
    val button2: Color,
    val accent: Color,
    val accentSoft: Color,
    val bar: Color,
    val warning: Color,
    /** Подложка под большими цифрами на небе; прозрачная, когда не нужна. */
    val scrim: Color,
    /**
     * Подложка под строками под температурой (состояние, максимум и минимум, «Ощущается», подписи): полная прозрачность
     * в этой полосе; прозрачная, когда небо там и так читаемо (см. `SkyPalette.infoScrim`).
     */
    val infoScrim: Color,
    /** Худший цвет неба под этими строками с учётом подложки; по нему подбираются оттенки максимума и минимума. */
    val infoBackdrop: Color,
    /** Истина, если текст тёмный: значки системных панелей тогда тоже тёмные. */
    val darkInk: Boolean,
    /** Насколько стекло дымчатое: 0 при тёмном тексте, 1 при светлом (см. `SkyPalette.smoke`). */
    val smoke: Float,
) {
    companion object {
        val Neutral = PogodaPalette(
            ink = PogodaColors.Ink,
            ink2 = PogodaColors.Ink2,
            glass = PogodaColors.Glass,
            glassLine = PogodaColors.GlassLine,
            glassHighlight = PogodaColors.GlassHighlight,
            buttonHighlight = PogodaColors.ButtonHighlight,
            shadow = PogodaColors.Shadow,
            sheen = PogodaColors.Sheen,
            buttonBackground = PogodaColors.ButtonBackground,
            buttonOn = PogodaColors.ButtonOn,
            button2 = PogodaColors.Button2,
            accent = PogodaColors.Accent,
            accentSoft = PogodaColors.AccentSoft,
            bar = PogodaColors.Bar,
            warning = PogodaColors.Warning,
            scrim = Color.Transparent,
            infoScrim = Color.Transparent,
            infoBackdrop = PogodaColors.SkyMiddle,
            darkInk = true,
            smoke = 0f,
        )
    }
}

val LocalPogodaPalette = compositionLocalOf { PogodaPalette.Neutral }

/** Палитра между [this] (доля 0) и [other] (доля 1). Булево значение берётся от той, что ближе. */
fun PogodaPalette.mixWith(other: PogodaPalette, fraction: Float): PogodaPalette = when {
    fraction <= 0f -> this
    fraction >= 1f -> other
    else -> PogodaPalette(
        ink = lerp(ink, other.ink, fraction),
        ink2 = lerp(ink2, other.ink2, fraction),
        glass = lerp(glass, other.glass, fraction),
        glassLine = lerp(glassLine, other.glassLine, fraction),
        glassHighlight = lerp(glassHighlight, other.glassHighlight, fraction),
        buttonHighlight = lerp(buttonHighlight, other.buttonHighlight, fraction),
        shadow = lerp(shadow, other.shadow, fraction),
        sheen = lerp(sheen, other.sheen, fraction),
        buttonBackground = lerp(buttonBackground, other.buttonBackground, fraction),
        buttonOn = lerp(buttonOn, other.buttonOn, fraction),
        button2 = lerp(button2, other.button2, fraction),
        accent = lerp(accent, other.accent, fraction),
        accentSoft = lerp(accentSoft, other.accentSoft, fraction),
        bar = lerp(bar, other.bar, fraction),
        warning = lerp(warning, other.warning, fraction),
        scrim = lerp(scrim, other.scrim, fraction),
        infoScrim = lerp(infoScrim, other.infoScrim, fraction),
        infoBackdrop = lerp(infoBackdrop, other.infoBackdrop, fraction),
        darkInk = if (fraction < 0.5f) darkInk else other.darkInk,
        smoke = smoke + (other.smoke - smoke) * fraction,
    )
}
