package io.github.dumahuch.pogodaapp.sky

/**
 * Шесть нарисованных картин неба. [pan] это куда смотрит кадр телефона на картине (0 слева, 1 справа):
 * свет рассвета слева, солнце днём и закат справа, луна слева. Значения из макета.
 */
internal enum class SkyScene(val fileName: String, val pan: Double) {
    Dawn("dawn", 0.06),
    Morning("morning", 0.42),
    Afternoon("afternoon", 1.0),
    Sunset("sunset", 0.86),
    Dusk("dusk", 0.90),
    Night("night", 0.04),
}

/** Доли шести картин в кадре (сумма единица) в порядке [SkyScene]. */
internal class SceneWeights(private val values: DoubleArray) {
    init {
        require(values.size == SkyScene.entries.size) { "Нужно ${SkyScene.entries.size} весов, получено ${values.size}" }
    }

    operator fun get(scene: SkyScene): Double = values[scene.ordinal]

    val total: Double get() = values.sum()

    /** Куда смотрит кадр: среднее положений картин с их весами. */
    val pan: Double
        get() {
            var sum = 0.0
            var weight = 0.0
            for (scene in SkyScene.entries) {
                sum += this[scene] * scene.pan
                weight += this[scene]
            }
            return if (weight > 0) sum / weight else 0.5
        }

    /** Картины, которые заметны в кадре (вес выше порога), в порядке [SkyScene]. */
    fun visible(threshold: Double = VISIBLE_THRESHOLD): List<SkyScene> = SkyScene.entries.filter { this[it] > threshold }

    /**
     * Оставляет [count] самых весомых картин, остальные обнуляет, а веса оставшихся масштабирует так,
     * чтобы общая сумма не изменилась. Нужно, чтобы в памяти было не больше [count] времён суток.
     */
    fun strongest(count: Int): SceneWeights {
        val keep = SkyScene.entries.sortedByDescending { this[it] }.take(count).toSet()
        val kept = keep.sumOf { this[it] }
        if (kept <= 0.0) return this
        val scale = total / kept
        return SceneWeights(DoubleArray(values.size) { i -> if (SkyScene.entries[i] in keep) values[i] * scale else 0.0 })
    }

    companion object {
        /** Картины легче этого веса в кадре не рисуются. */
        const val VISIBLE_THRESHOLD = 0.004
    }
}

/**
 * Какие картины и в каких долях видны при данной высоте солнца. Не по часам: зимой и летом рассвет
 * приходит в разное время. Утро: ночь → рассвет → утро. Вечер: день → закат → сумерки → ночь.
 * Около солнечного полудня одна ветка плавно сменяет другую.
 */
internal object SkyTimeOfDay {

    /** Насколько уже «вечер», от 0 (утренняя ветка) до 1 (вечерняя): плавно около солнечного полудня. */
    fun evening(solarHour: Double): Double = smooth(11.2, 12.8, solarHour)

    fun weights(elevation: Double, solarHour: Double): SceneWeights {
        val f = evening(solarHour)
        val s1 = smooth(-17.0, -6.0, elevation)
        val s2 = smooth(0.0, 10.0, elevation)
        val s3 = smooth(3.0, 14.0, elevation)
        val s4 = smooth(-8.0, -1.5, elevation)
        val s5 = smooth(-16.0, -8.0, elevation)
        // Утренняя ветка.
        val mNight = 1 - s1
        val mDawn = s1 * (1 - s2)
        val mMorning = s2
        // Вечерняя ветка.
        val vAfternoon = s3
        val vSunset = (1 - s3) * s4
        val vDusk = (1 - s3) * (1 - s4) * s5
        val vNight = (1 - s3) * (1 - s4) * (1 - s5)
        val out = DoubleArray(SkyScene.entries.size)
        out[SkyScene.Dawn.ordinal] = (1 - f) * mDawn
        out[SkyScene.Morning.ordinal] = (1 - f) * mMorning
        out[SkyScene.Afternoon.ordinal] = f * vAfternoon
        out[SkyScene.Sunset.ordinal] = f * vSunset
        out[SkyScene.Dusk.ordinal] = f * vDusk
        out[SkyScene.Night.ordinal] = (1 - f) * mNight + f * vNight
        return SceneWeights(out)
    }
}
