package io.github.dumahuch.pogodaapp.sky

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** Цвет с прозрачностью (доля от 0 до 1). */
internal class Rgba(val rgb: Rgb, val alpha: Double)

/**
 * Контраст текста в худших местах фона. Для мелкого текста нужно не меньше [SkyTheme.TARGET],
 * для больших цифр достаточно 3. [info] это контраст основного цвета текста строк под температурой
 * (состояние, максимум и минимум, «Ощущается как», подписи) на небе с подложкой [SkyPalette.infoScrim].
 */
internal class ContrastReport(
    val hero: Double,
    val cards: Double,
    val alerts: Double,
    val buttons: Double,
    val info: Double = 0.0,
)

/** Цвета интерфейса, посчитанные под текущее небо. */
internal class SkyPalette(
    val ink: Rgb,
    val ink2: Rgba,
    val glass: Rgba,
    val glassAlert: Rgba,
    val buttonBackground: Rgba,
    val buttonOn: Rgba,
    val sheen: Rgba,
    val glassLine: Rgba,
    /** Светлая линия по верхней кромке карточки (в макете `--glass-hi`). */
    val glassHighlight: Rgba,
    /** То же для кнопок и нижней панели (`--btn-hi`). */
    val buttonHighlight: Rgba,
    /** Тень под кнопками и нижней панелью (`--shadow`). */
    val shadow: Rgba,
    val button2: Rgba,
    val accent: Rgb,
    val accentSoft: Rgba,
    val bar: Rgb,
    val warning: Rgb,
    /** Подложка под большими цифрами: стекла там нет, поэтому добавляется едва заметная вуаль. */
    val scrim: Rgba,
    /**
     * Подложка под строками под температурой (состояние, максимум и минимум, «Ощущается», подписи): стекла и там нет.
     * Прозрачность это полная прозрачность подложки в этой полосе (она не складывается с [scrim], а берётся наибольшая).
     * Нулевая, если небо там и так даёт читаемый текст.
     */
    val infoScrim: Rgba,
    /** Худший цвет неба под этими строками с учётом подложки: по нему подбираются оттенки максимума и минимума. */
    val infoBackdrop: Rgb,
    /** Истина, пока текст тёмный (на светлом небе). */
    val darkInk: Boolean,
    /**
     * Насколько стекло «дымчатое»: 0 при тёмном тексте, 1 при светлом, между ними при плавной смене.
     * В виде «Авто» от этого зависят яркость и насыщенность фона под стеклом и зерно.
     */
    val smoke: Double,
    val contrast: ContrastReport,
)

/** Как справляется один из двух вариантов текста (тёмный на светлом стекле или светлый на тёмном). */
internal class InkSide(
    /** Плотность вуали под цифрами, при которой цифры читаемы. */
    val scrim: Double,
    /** Контраст цифр на небе с этой вуалью. */
    val hero: Double,
    /** Плотность стекла карточек и оповещений: не ниже минимума и достаточная для контраста. */
    val glassOpacity: Double,
    val buttonOpacity: Double,
    /** Сколько нужно для читаемости вообще, без нижней границы (на случай ручной прозрачности). */
    val needCards: Double,
    val needAlerts: Double,
    val needButtons: Double,
    /** Общая оценка варианта: чем выше, тем лучше. */
    val score: Double,
    /** Плотность подложки под строками под температурой, при которой основной цвет текста держит [SkyTheme.INFO_TARGET]. */
    val infoScrim: Double,
    /** Худший цвет неба под этими строками с этой подложкой. */
    val infoBackdrop: Rgb,
)

/** Оценка двух вариантов текста на текущем небе. */
internal class InkScores(
    val backdrops: Backdrops,
    /** Заливка стекла под тёмный текст (светлая) и под светлый (тёмная), с оттенком неба. */
    val lightTint: Rgb,
    val darkTint: Rgb,
    val darkInk: InkSide,
    val lightInk: InkSide,
) {
    /** Цвет неба в среднем: по нему видно, насколько текст читаем прямо на небе. */
    val mid: Rgb get() = mix(mix(backdrops.top, backdrops.hor, 0.5), backdrops.mid, 0.5)
}

/**
 * Динамическая тема: по цветам фона выбирает цвет текста (тёмный или светлый) и плотность стекла.
 * Формулы взяты из макета (inkScores и themeVars), где они проверены в браузере.
 * Вид стекла «Авто» (решение Димы): цвета и плотность считаются как для лёгкого стекла, а насколько оно дымчатое,
 * задаёт положение переключателя текста ([SkyPalette.smoke]); как это рисуется, см. `ui/components/Glass.kt`.
 */
internal object SkyTheme {
    val INK_DARK = Rgb(16.0, 23.0, 34.0)
    val INK_LIGHT = Rgb(244.0, 247.0, 251.0)
    val GLASS_DARK = Rgb(12.0, 19.0, 31.0)

    /** Контраст, который держит мелкий текст (WCAG AA). */
    const val TARGET = 4.5

    /** Доля основного цвета в цвете вторичного текста (Ink2). */
    const val INK2_ALPHA = 0.84

    /** Кнопки светлее карточек на эту долю белого. */
    const val BUTTON_LIGHTER = 0.45

    /** Минимальная непрозрачность стекла: светлого и тёмного; нижней панели светлой и тёмной. */
    const val GA_LIGHT = 0.26
    const val GA_DARK = 0.26
    const val GB_LIGHT = 0.52
    const val GB_DARK = 0.46

    /** Потолок вуали под цифрами. */
    const val SCRIM_MAX = 0.36

    /**
     * Строки под температурой лежат прямо на небе, без стекла, и набраны основным цветом текста (не вторичным).
     * Подложка добавляется только там, где небо само не даёт [INFO_TARGET] (с запасом над 4,5: поверх основного цвета
     * подмешивается оттенок максимума и минимума, он забирает часть контраста); потолок как у вуали под цифрами.
     */
    const val INFO_TARGET = 5.0
    const val INFO_SCRIM_MAX = 0.36

    /** Прозрачность карточек по умолчанию: 80 % прозрачности по шкале макета (0,94 − 0,009 × 80). */
    const val CARDS_MIN_OPACITY = 0.22

    /** Вторичный текст на фоне [bg]: основной цвет с долей [INK2_ALPHA]. */
    fun effectiveInk(bg: Rgb, ink: Rgb): Rgb = mix(bg, ink, INK2_ALPHA)

    /** Наименьшая непрозрачность заливки [tint] от [from] до [to], при которой вторичный текст [ink] держит контраст. */
    fun needAlpha(bg: Rgb, tint: Rgb, ink: Rgb, from: Double, to: Double): Double {
        var a = from
        while (a < to) {
            val glass = mix(bg, tint, a)
            if (contrast(glass, effectiveInk(glass, ink)) >= TARGET) return a
            a += 0.02
        }
        return to
    }

    /** Наименьшая непрозрачность подложки [tint] от нуля до [cap], при которой цвет [ink] держит контраст [target] на фоне [bg]. */
    fun needInkAlpha(bg: Rgb, tint: Rgb, ink: Rgb, target: Double, cap: Double): Double {
        var a = 0.0
        while (a < cap) {
            if (contrast(mix(bg, tint, a), ink) >= target) return a
            a += 0.02
        }
        return cap
    }

    fun scores(b: Backdrops): InkScores {
        val tintBase = mix(b.mid, b.hor, 0.45)
        val lightTint = mix(Rgb.White, tintBase, 0.36)
        val darkTint = mix(GLASS_DARK, tintBase, 0.26)
        return InkScores(
            backdrops = b,
            lightTint = lightTint,
            darkTint = darkTint,
            darkInk = side(b, true, INK_DARK, lightTint, GA_LIGHT, mix(lightTint, Rgb.White, BUTTON_LIGHTER), GB_LIGHT),
            lightInk = side(b, false, INK_LIGHT, darkTint, GA_DARK, darkTint, GB_DARK),
        )
    }

    private fun side(b: Backdrops, underDark: Boolean, ink: Rgb, tint: Rgb, ga: Double, button: Rgb, gb: Double): InkSide {
        fun under(r: RegionColors) = if (underDark) r.underDarkInk else r.underLightInk
        val hero = under(b.hero)
        val alertBg = under(b.alert)
        val cardBg = under(b.card)
        val dockBg = under(b.dock)
        val scrim = needAlpha(hero, tint, ink, 0.0, SCRIM_MAX)
        val heroBg = mix(hero, tint, scrim)
        val heroContrast = contrast(heroBg, effectiveInk(heroBg, ink))
        val glass = max(needAlpha(alertBg, tint, ink, ga, 0.76), needAlpha(cardBg, tint, ink, ga, 0.76))
        val buttons = max(needAlpha(dockBg, button, ink, gb, 0.8), needAlpha(hero, button, ink, gb, 0.8))
        val infoBg = under(b.info)
        val infoScrim = needInkAlpha(infoBg, tint, ink, INFO_TARGET, INFO_SCRIM_MAX)
        return InkSide(
            scrim = scrim,
            hero = heroContrast,
            glassOpacity = glass,
            buttonOpacity = buttons,
            needCards = needAlpha(cardBg, tint, ink, 0.0, 0.95),
            needAlerts = needAlpha(alertBg, tint, ink, 0.0, 0.95),
            needButtons = max(needAlpha(dockBg, button, ink, 0.0, 0.95), needAlpha(hero, button, ink, 0.0, 0.95)),
            score = min(heroContrast, 6.0) - 6 * (glass - ga) - 5 * scrim,
            infoScrim = infoScrim,
            infoBackdrop = mix(infoBg, tint, infoScrim),
        )
    }

    /**
     * Палитра для положения переключателя [k]: 0 это тёмный текст на светлом стекле, 1 светлый на тёмном.
     * Промежуточные значения нужны для плавной смены; в середине стекло чуть плотнее, чтобы текст не терялся.
     */
    fun palette(q: InkScores, k: Double): SkyPalette {
        val d = q.darkInk
        val l = q.lightInk
        val ink = mix(INK_DARK, INK_LIGHT, k)
        val tint = mix(q.lightTint, q.darkTint, k)
        val buttonTint = mix(mix(q.lightTint, Rgb.White, BUTTON_LIGHTER), q.darkTint, k)
        val bump = sin(PI * k)
        val alertsAuto = lerp(d.glassOpacity, l.glassOpacity, k) + 0.06 * bump
        val buttonsAuto = lerp(d.buttonOpacity, l.buttonOpacity, k) + 0.05 * bump
        val cards = max(CARDS_MIN_OPACITY, lerp(d.needCards, l.needCards, k))
        val under: (RegionColors) -> Rgb = { if (k < 0.5) it.underDarkInk else it.underLightInk }
        val b = q.backdrops
        fun readable(bg: Rgb, fill: Rgb, alpha: Double): Double {
            val glass = mix(bg, fill, alpha)
            return contrast(glass, effectiveInk(glass, ink))
        }
        val accent = mix(Rgb(28.0, 86.0, 160.0), Rgb(170.0, 208.0, 250.0), k)
        val infoBackdrop = mix(d.infoBackdrop, l.infoBackdrop, k)
        return SkyPalette(
            ink = ink,
            ink2 = Rgba(ink, INK2_ALPHA),
            glass = Rgba(tint, cards),
            glassAlert = Rgba(tint, alertsAuto),
            buttonBackground = Rgba(buttonTint, buttonsAuto),
            buttonOn = Rgba(Rgb.White, lerp(0.8, 0.22, k)),
            sheen = Rgba(Rgb.White, lerp(0.2, 0.07, k)),
            glassLine = Rgba(ink, 0.12),
            glassHighlight = Rgba(mix(Rgb.White, tint, 0.1), lerp(0.30, 0.10, k)),
            buttonHighlight = Rgba(Rgb.White, lerp(0.8, 0.2, k)),
            shadow = Rgba(mix(Rgb(20.0, 34.0, 56.0), Rgb(0.0, 0.0, 0.0), k), lerp(0.16, 0.34, k)),
            button2 = Rgba(ink, 0.2),
            accent = accent,
            accentSoft = Rgba(accent, 0.2),
            bar = mix(Rgb(46.0, 98.0, 142.0), Rgb(152.0, 202.0, 250.0), k),
            warning = mix(Rgb(192.0, 105.0, 45.0), Rgb(242.0, 186.0, 98.0), k),
            scrim = Rgba(tint, lerp(d.scrim, l.scrim, k)),
            infoScrim = Rgba(tint, lerp(d.infoScrim, l.infoScrim, k)),
            infoBackdrop = infoBackdrop,
            darkInk = k < 0.5,
            smoke = k,
            contrast = ContrastReport(
                hero = lerp(d.hero, l.hero, k),
                cards = readable(under(b.card), tint, cards),
                alerts = readable(under(b.alert), tint, alertsAuto),
                buttons = min(readable(under(b.dock), buttonTint, buttonsAuto), readable(b.hero.mean, buttonTint, buttonsAuto)),
                info = contrast(infoBackdrop, ink),
            ),
        )
    }
}

/**
 * Выбор цвета текста с задержкой: вариант меняется, только если другой лучше заметно (на 0,6 балла),
 * и переход идёт плавно (около трети секунды), чтобы текст не мигал при дрейфе облаков.
 */
internal class InkSwitch {
    /** Текущее положение переключателя: 0 тёмный текст, 1 светлый. */
    var position: Double = 0.0
        private set
    private var target: Double = 0.0
    private var started = false

    fun update(q: InkScores, seconds: Double): Double {
        val dark = q.darkInk.score
        val light = q.lightInk.score
        if (!started) {
            target = if (light > dark) 1.0 else 0.0
            position = target
            started = true
        } else if (target == 0.0 && light - dark > SWITCH_MARGIN) {
            target = 1.0
        } else if (target == 1.0 && dark - light > SWITCH_MARGIN) {
            target = 0.0
        }
        if (position != target) {
            val step = seconds * SWITCH_SPEED
            position = if (abs(target - position) <= step) target else position + sign(target - position) * step
        }
        return position
    }

    /** Выбранный вариант, к которому идёт переход. */
    val targetPosition: Double get() = target

    private companion object {
        const val SWITCH_MARGIN = 0.6
        const val SWITCH_SPEED = 3.2
    }
}
