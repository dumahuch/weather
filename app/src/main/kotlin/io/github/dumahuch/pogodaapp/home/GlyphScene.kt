package io.github.dumahuch.pogodaapp.home

/**
 * Геометрия значков погоды (step-42, по образцам Димы 07.11.2026). Один рисунок на оба набора: стеклянный и контурный
 * отличаются только исполнением (стекло с размытием того, что за облаком, или линия с зазором), форма та же.
 * Всё в единицах квадрата 24 × 24, сверху вниз. Чистая логика без Android: [GlyphSceneTest]; рисует `GlyphPainter`.
 *
 * Облако несимметричное: крупная «голова» левее центра, малая доля слева, средняя справа, плоское дно со скруглёнными
 * концами; ширина к высоте около 1,5.
 */
internal object CloudShape {
    /** Круги облака (центр x, центр y, радиус) и прямоугольник дна (лево, верх, право, низ). */
    val circles = listOf(Circle(6f, 15f, 3.5f), Circle(10f, 10.5f, 5.2f), Circle(17.5f, 14f, 4.5f))
    val base = Box(6f, 12f, 17.5f, 18.5f)
    const val TOP = 5.3f
    const val BOTTOM = 18.5f
    const val LEFT = 2.5f
    const val RIGHT = 22f
}

internal data class Circle(val x: Float, val y: Float, val r: Float)
internal data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Облако [CloudShape], уменьшенное в [k] раз и сдвинутое на [dx], [dy] (после уменьшения). */
internal data class CloudPlace(val dx: Float, val dy: Float, val k: Float) {
    val top: Float get() = CloudShape.TOP * k + dy
    val bottom: Float get() = CloudShape.BOTTOM * k + dy
    val left: Float get() = CloudShape.LEFT * k + dx
    val right: Float get() = CloudShape.RIGHT * k + dx
}

/** Отрезок линии с круглыми концами. */
internal data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * Линия ветра: горизонталь от [fromX] до [toX] на высоте [y], на правом конце завиток по окружности с центром
 * ([cx], [cy]) радиуса [r]: начало дуги [startDeg], размах [sweepDeg] (градусы, по часовой при y вниз, как в Android).
 * Без завитка [r] = 0.
 */
internal data class WindStroke(
    val fromX: Float,
    val toX: Float,
    val y: Float,
    val cx: Float = 0f,
    val cy: Float = 0f,
    val r: Float = 0f,
    val startDeg: Float = 0f,
    val sweepDeg: Float = 0f,
)

/** Части рисунка за облаком (или без облака), в порядке рисования. */
internal sealed interface GlyphPart {
    /** Нижний край части с учётом толщины линии (для линий ширина задана в самой части). */
    val bottom: Float

    data class Sun(val x: Float, val y: Float, val r: Float, val rays: Boolean) : GlyphPart {
        override val bottom get() = y + if (rays) r + RAY_TO + RAY_WIDTH / 2 else r
    }

    /** Полумесяц: круг без смещённого вправо вверх круга поменьше. */
    data class Moon(val x: Float, val y: Float, val r: Float) : GlyphPart {
        val cutX get() = x + 0.5f * r
        val cutY get() = y - 0.38f * r
        val cutR get() = 0.82f * r
        override val bottom get() = y + r
    }

    /** Заднее облако («облачно»): поменьше, выше и прозрачнее переднего. */
    data class BackCloud(val place: CloudPlace) : GlyphPart {
        override val bottom get() = place.bottom
    }

    /** Штрихи дождя (короткие отрезки, решение Димы 07.11.2026: не длинные струи). */
    data class Rain(val dashes: List<Segment>) : GlyphPart {
        override val bottom get() = dashes.maxOf { maxOf(it.y1, it.y2) } + DASH_WIDTH / 2
    }

    /** Снежинка-звёздочка: три отрезка через центр под 60°, концы круглые. */
    data class Star(val x: Float, val y: Float, val r: Float) : GlyphPart {
        val width get() = if (r > 2f) 1.35f else 1.1f
        override val bottom get() = y + r + width / 2
    }

    data class Bolt(val points: List<Pair<Float, Float>>) : GlyphPart {
        override val bottom get() = points.maxOf { it.second }
    }

    data class Wind(val strokes: List<WindStroke>) : GlyphPart {
        override val bottom get() = strokes.maxOf { maxOf(it.y, it.cy + it.r) } + WIND_WIDTH / 2
    }

    /** Полосы тумана: от [Segment.x1] до [Segment.x2] на высоте y (у стекла это плоская пилюля высотой [FOG_HEIGHT]). */
    data class Fog(val bars: List<Segment>) : GlyphPart {
        override val bottom get() = bars.maxOf { it.y1 } + FOG_HEIGHT / 2
    }

    companion object {
        const val RAY_FROM = 2f
        const val RAY_TO = 3.6f
        const val RAY_WIDTH = 1.45f
        const val DASH_WIDTH = 1.5f
        const val WIND_WIDTH = 1.45f
        const val FOG_HEIGHT = 2.5f
    }
}

/** Рисунок значка: части за облаком и переднее облако ([front] пусто: облака нет, например туман или ветер). */
internal class GlyphScene(val parts: List<GlyphPart>, val front: CloudPlace?) {
    /** Нижний край облака (у тумана и ветра нижний край рисунка): по нему значки выравниваются у цифр. */
    val cloudBottom: Float get() = front?.bottom ?: parts.maxOf { it.bottom }

    /** Нижний край всего рисунка (без толщины контура облака). */
    val bottom: Float get() = maxOf(front?.bottom ?: 0f, parts.maxOfOrNull { it.bottom } ?: 0f)
}

internal object GlyphScenes {
    private val partlyCloud = CloudPlace(-0.6f, 3.6f, 0.84f)
    private val wetCloud = CloudPlace(0.1f, -2.6f, 0.92f)
    private val windyWetCloud = CloudPlace(-1.2f, -1.8f, 0.88f)

    /** Значок для «Главной» и уведомления. */
    fun of(icon: HeroIcon): GlyphScene = when (icon) {
        HeroIcon.CloudSun -> GlyphScene(listOf(GlyphPart.Sun(16.3f, 8.4f, 4.6f, rays = false)), partlyCloud)
        HeroIcon.CloudMoon -> GlyphScene(listOf(GlyphPart.Moon(16.8f, 8f, 5f)), partlyCloud)
        HeroIcon.Clouds -> GlyphScene(listOf(GlyphPart.BackCloud(CloudPlace(7.4f, 0.4f, 0.7f))), CloudPlace(-0.8f, 4.4f, 0.86f))
        HeroIcon.CloudWind -> GlyphScene(listOf(wind(5.6f, 4.4f, 0.78f)), CloudPlace(-1.4f, -0.8f, 0.9f))
        HeroIcon.Rain -> GlyphScene(
            listOf(rain(listOf(Column(8f, 12.6f, 3), Column(11.5f, 14.4f, 2), Column(15f, 12.6f, 3), Column(18.4f, 14.4f, 2)), RAIN_SLANT)),
            wetCloud,
        )
        HeroIcon.RainWind -> GlyphScene(
            listOf(
                rain(listOf(Column(5.4f, 12.6f, 3), Column(8.6f, 14.4f, 2), Column(11.8f, 12.6f, 3)), WINDY_RAIN_SLANT),
                wind(5.8f, 3.6f, 0.78f),
            ),
            windyWetCloud,
        )
        HeroIcon.Snow -> GlyphScene(
            listOf(GlyphPart.Star(11.6f, 18.2f, 3.2f), GlyphPart.Star(17.6f, 20.9f, 1.8f), GlyphPart.Star(6.2f, 20.6f, 1.6f)),
            wetCloud,
        )
        HeroIcon.SnowWind -> GlyphScene(
            listOf(
                GlyphPart.Star(7.4f, 18f, 2.5f),
                GlyphPart.Star(3.8f, 21.2f, 1.4f),
                GlyphPart.Star(11f, 21.4f, 1.4f),
                wind(5.8f, 3.6f, 0.78f),
            ),
            windyWetCloud,
        )
        HeroIcon.Storm -> GlyphScene(listOf(GlyphPart.Bolt(BOLT.map { (x, y) -> x - 0.4f to y - 0.6f })), wetCloud)
        HeroIcon.Wind -> GlyphScene(listOf(wind(-1.5f, -5.5f, 1.15f)), null)
        HeroIcon.Fog -> GlyphScene(
            listOf(GlyphPart.Fog(listOf(Segment(4f, 9.5f, 17f, 9.5f), Segment(7f, 14f, 20f, 14f), Segment(4f, 18.5f, 15.5f, 18.5f)))),
            null,
        )
    }

    /** Ясное небо для уведомления (на «Главной» солнце и луна нарисованы на небе): солнце с лучами или месяц по центру. */
    fun clear(isDay: Boolean): GlyphScene =
        if (isDay) GlyphScene(listOf(GlyphPart.Sun(12f, 12f, 5f, rays = true)), null)
        else GlyphScene(listOf(GlyphPart.Moon(12.5f, 11.5f, 7f)), null)

    /** Колонка штрихов дождя: x наверху, начало первого штриха и сколько штрихов. */
    private data class Column(val x: Float, val start: Float, val count: Int)

    /** Штрих длиной [DASH], следующий через [DASH_PERIOD]; колонка наклонена влево на [slant] по x на единицу высоты. */
    private fun rain(columns: List<Column>, slant: Float) = GlyphPart.Rain(
        columns.flatMap { c ->
            (0 until c.count).map { i ->
                val y1 = c.start + i * DASH_PERIOD
                val y2 = y1 + DASH
                Segment(c.x - slant * (y1 - RAIN_TOP), y1, c.x - slant * (y2 - RAIN_TOP), y2)
            }
        },
    )

    /** Три линии ветра: две с завитком (вверх и вниз) и короткая; [k] масштаб, [dx], [dy] сдвиг после масштаба. */
    private fun wind(dx: Float, dy: Float, k: Float): GlyphPart.Wind {
        fun x(v: Float) = v * k + dx
        fun y(v: Float) = v * k + dy
        return GlyphPart.Wind(
            listOf(
                WindStroke(x(8.5f), x(17.6f), y(16.6f), x(17.6f), y(14.3f), 2.3f * k, 90f, -270f),
                WindStroke(x(6f), x(20f), y(19.6f), x(20f), y(21.7f), 2.1f * k, -90f, 270f),
                WindStroke(x(11f), x(15f), y(22.6f)),
            ),
        )
    }

    private const val DASH = 2.2f
    private const val DASH_PERIOD = 3.8f
    private const val RAIN_TOP = 11f
    private const val RAIN_SLANT = 0.25f
    private const val WINDY_RAIN_SLANT = 0.45f

    /** Молния: вершины многоугольника (жёлтая в обоих наборах, решение Димы 07.11.2026). */
    private val BOLT = listOf(13.2f to 10.5f, 8.6f to 17.2f, 11.8f to 17.2f, 10.2f to 23f, 16.2f to 15.2f, 12.9f to 15.2f, 14.8f to 10.5f)
}
