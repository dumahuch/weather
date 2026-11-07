package io.github.dumahuch.pogodaapp.sky

import kotlin.math.max

/**
 * Форма подложек на небе по высоте: подложка под большими цифрами и подложка под строками под температурой. Обе одного
 * оттенка, поэтому в каждой точке берётся наибольшая из двух плотностей, а не сумма: там, где они перекрываются, иначе
 * получалась бы двойная вуаль.
 *
 * До step-40 обе стояли по фиксированным координатам макета (экран 412×892 dp, [LEGACY]); теперь «Главная» присылает
 * границы настоящих блоков ([around]), и подложки идут за ними: блок цифр динамичный, подложка тоже.
 * Чистая логика без Android: [ScrimShapeTest].
 */
internal object ScrimShape {

    /** Высота экрана макета, к которой привязаны числа [LEGACY]. */
    const val SCREEN_HEIGHT = 892.0

    /**
     * Одна подложка: плотность 0 выше [zeroTop] и ниже [zeroBottom], полная между [fullFrom] и [fullTo], между ними
     * линейно. Все числа в одних единицах (dp макета или пиксели окна).
     */
    class Band(val zeroTop: Double, val fullFrom: Double, val fullTo: Double, val zeroBottom: Double) {
        fun at(y: Double): Double = when {
            y <= zeroTop || y >= zeroBottom -> 0.0
            y < fullFrom -> (y - zeroTop) / (fullFrom - zeroTop)
            y <= fullTo -> 1.0
            else -> (zeroBottom - y) / (zeroBottom - fullTo)
        }

        val corners: List<Double> get() = listOf(zeroTop, fullFrom, fullTo, zeroBottom)
    }

    /** Подложка под цифрами и подложка под строками под температурой, в одних единицах. */
    class Bands(val hero: Band, val info: Band)

    /**
     * Подложка с полной плотностью от [top] до [bottom] блока и плавным краем шириной [feather] снаружи (если блок
     * тоньше двух краёв, они всё равно не пересекаются: край внутрь блока не заходит).
     */
    fun band(top: Double, bottom: Double, feather: Double): Band =
        Band(top - feather, top, maxOf(top, bottom), maxOf(top, bottom) + feather)

    /** Подложки по границам настоящих блоков (пиксели окна): цифры [heroTop]…[heroBottom], строки [infoTop]…[infoBottom]. */
    fun around(
        heroTop: Double,
        heroBottom: Double,
        infoTop: Double,
        infoBottom: Double,
        heroFeather: Double,
        infoFeather: Double,
    ): Bands = Bands(band(heroTop, heroBottom, heroFeather), band(infoTop, infoBottom, infoFeather))

    /**
     * Прежняя форма по фиксированным координатам макета. Под цифрами: плотность 0 до 96 dp, полная с 20 % до 72 % пути
     * до 440 dp, затем снова 0. Под строками под температурой (с 0.5.4 верхний блок стоит посередине свободного места,
     * строки лежат между 375 и 495 dp): 300, 335, 530, 575 с запасом по краям. Нужна, пока «Главная» границ не прислала
     * (первый кадр, другие вкладки до первого показа «Главной»).
     */
    val LEGACY: Bands = Bands(
        Band(96.0, 96.0 + 0.2 * (440.0 - 96.0), 96.0 + 0.72 * (440.0 - 96.0), 440.0),
        Band(300.0, 335.0, 530.0, 575.0),
    )

    /** Шаг, с которым снимается итоговая форма на высоте [SCREEN_HEIGHT] (кроме углов, они берутся точно). */
    private const val SAMPLE_STEP = 20.0

    /** Плотность в точке [y] при плотностях [hero] и [info] в полосах [bands]. */
    fun alphaAt(y: Double, hero: Double, info: Double, bands: Bands = LEGACY): Double =
        max(hero * bands.hero.at(y), info * bands.info.at(y))

    /**
     * Остановки градиента по высоте [height] (в единицах [bands]): пары (доля высоты от 0 до 1, плотность).
     * Углы, лежащие вне экрана, отбрасываются: края 0 и [height] считаются по той же форме.
     */
    fun stops(hero: Double, info: Double, bands: Bands = LEGACY, height: Double = SCREEN_HEIGHT): List<Pair<Float, Float>> {
        val ys = sortedSetOf(0.0, height)
        (bands.hero.corners + bands.info.corners).filterTo(ys) { it > 0.0 && it < height }
        val step = SAMPLE_STEP * height / SCREEN_HEIGHT
        var y = step
        while (y < height) {
            ys.add(y)
            y += step
        }
        return ys.map { (it / height).toFloat() to alphaAt(it, hero, info, bands).toFloat() }
    }
}
