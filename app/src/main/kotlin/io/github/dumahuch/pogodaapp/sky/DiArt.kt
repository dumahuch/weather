package io.github.dumahuch.pogodaapp.sky

import kotlin.math.max

/** Где на экране лежит картинка: левый верхний угол и масштаб (во сколько раз она растянута). */
internal data class Placement(val left: Double, val top: Double, val scale: Double)

/**
 * Фон «Ди» (решение Димы 05.11.2026): слоистые бумажные горы, шесть времён суток по шесть слоёв 1024×1536
 * (небо и пять гряд сзади вперёд), композиция гряд одна во всех сценах. Облака из отдельных спрайтов разложены
 * на два листа (светлые и грозовые), подкрашиваются под время суток и лежат между небом и горами, под погодной вуалью.
 *
 * Слои двигает время суток (по горизонтали, ближние гряды сильнее дальних) и прокрутка экрана (по вертикали, тоже
 * ближние сильнее). Слои растянуты на 12 % выше экрана и прижаты к верху, поэтому при сдвиге снизу дыр нет.
 * Геометрия общая для рисования и для расчёта цветов интерфейса.
 */
internal object DiArt {

    const val CANVAS_W = 1024.0
    const val CANVAS_H = 1536.0
    const val SHEET_W = 2048.0
    const val SHEET_H = 768.0

    /** Слои сзади вперёд. [depth] от 0 (небо стоит) до 1 (ближняя гряда движется сильнее всех); соотношение из README набора (−8, −20, −34, −50, −70 px). */
    enum class Layer(val file: String, val depth: Double) {
        Sky("sky", 0.0),
        Far("far", 8.0 / 70),
        Mid("mid", 20.0 / 70),
        Yellow("yellow", 34.0 / 70),
        Coral("coral", 50.0 / 70),
        Front("front", 1.0),
    }

    /** Насколько слой выше экрана: запас для сдвига прокруткой (на 10–15 %, как советует README набора). */
    const val OVERSIZE = 1.12

    /** Ширина слоя не меньше ширины экрана с этим запасом (на случай широкого экрана). */
    private const val MIN_WIDTH = 1.1

    /** Насколько ход кадра по времени суток слабее хода сцены: свет в сценах «Ди» не сбоку, а по центру. */
    private const val PAN_SPREAD = 0.35

    /** Ближняя гряда по горизонтали ходит на 60 % сильнее неба: от этого глубина. */
    private const val PAN_DEPTH = 0.6

    /** Самый большой подъём гряд прокруткой, доля высоты экрана (меньше запаса [OVERSIZE] − 1). */
    const val MAX_LIFT = 0.10

    /** Гряды поднимаются на четверть пути прокрутки (ближняя), дальние меньше. */
    private const val SCROLL_FACTOR = 0.25

    /** Лист облаков: высота доля высоты экрана, отступ сверху доля высоты экрана. */
    const val CLOUD_HEIGHT = 0.52
    const val CLOUD_TOP = 0.02

    /** Имя файла слоя без расширения: имена набора («day», «twilight»). */
    fun file(scene: SkyScene, layer: Layer): String = "${prefix(scene)}_${layer.file}"

    fun prefix(scene: SkyScene): String = when (scene) {
        SkyScene.Dawn -> "dawn"
        SkyScene.Morning -> "morning"
        SkyScene.Afternoon -> "day"
        SkyScene.Sunset -> "sunset"
        SkyScene.Dusk -> "twilight"
        SkyScene.Night -> "night"
    }

    /** Куда смотрит кадр (0…1): ход сцены, сжатый к середине. */
    fun pan(weights: SceneWeights): Double = 0.5 + (weights.pan - 0.5) * PAN_SPREAD

    /** Подъём гряд по прокрутке [scrollPx] на экране высотой [heightPx]: вниз положительный, протягивание вниз опускает гряды. */
    fun liftOf(scrollPx: Double, heightPx: Double): Double {
        if (heightPx <= 0) return 0.0
        return (scrollPx * SCROLL_FACTOR / heightPx).coerceIn(-MAX_LIFT, MAX_LIFT)
    }

    /** Где лежит слой [layer] на экране [width]×[height] при ходе кадра [pan] и подъёме [lift]. */
    fun place(layer: Layer, width: Double, height: Double, pan: Double, lift: Double): Placement {
        val scale = max(OVERSIZE * height / CANVAS_H, MIN_WIDTH * width / CANVAS_W)
        val room = CANVAS_W * scale - width
        val t = clamp01(0.5 + (pan - 0.5) * (1 + PAN_DEPTH * layer.depth))
        val top = -lift.coerceIn(-MAX_LIFT, MAX_LIFT) * height * layer.depth
        return Placement(left = -t * room, top = top, scale = scale)
    }

    /** Где лежит лист облаков: [position] от 0 (левый край листа у левого края экрана) до 1 (правый у правого). */
    fun placeClouds(width: Double, height: Double, position: Double): Placement {
        val scale = max(CLOUD_HEIGHT * height / SHEET_H, MIN_WIDTH * width / SHEET_W)
        val room = SHEET_W * scale - width
        return Placement(left = -clamp01(position) * room, top = CLOUD_TOP * height, scale = scale)
    }

    /** Подкраска светлых бумажных облаков под время суток (умножается на цвет облака). */
    fun tint(scene: SkyScene): Rgb = when (scene) {
        SkyScene.Dawn -> Rgb(250.0, 222.0, 232.0)
        SkyScene.Morning -> Rgb(255.0, 246.0, 234.0)
        SkyScene.Afternoon -> Rgb(255.0, 255.0, 255.0)
        SkyScene.Sunset -> Rgb(255.0, 206.0, 182.0)
        SkyScene.Dusk -> Rgb(206.0, 184.0, 226.0)
        SkyScene.Night -> Rgb(128.0, 142.0, 182.0)
    }

    /** Подкраска для смеси времён суток: среднее по долям. */
    fun tint(weights: SceneWeights): Rgb {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var sum = 0.0
        for (scene in SkyScene.entries) {
            val w = weights[scene]
            if (w <= 0) continue
            val c = tint(scene)
            r += c.r * w; g += c.g * w; b += c.b * w
            sum += w
        }
        return if (sum > 0) Rgb(r / sum, g / sum, b / sum) else tint(SkyScene.Afternoon)
    }
}
