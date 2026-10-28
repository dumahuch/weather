package io.github.dumahuch.pogodaapp.sky

/**
 * Цвета неба «по формулам»: верх, середина и горизонт в зависимости от высоты солнца и погоды.
 * Картины неба нарисованы, но эти три цвета нужны для погодной вуали (пасмурное небо уходит
 * в ровный серый того же времени суток) и для общего оттенка стекла. Таблицы из макета.
 */
internal class Veil(val top: Rgb, val mid: Rgb, val hor: Rgb)

internal object SkyColors {

    private class Key(val elevation: Double, val top: Rgb, val mid: Rgb, val hor: Rgb)

    private fun key(e: Double, top: Int, mid: Int, hor: Int) = Key(e, Rgb.hex(top), Rgb.hex(mid), Rgb.hex(hor))

    private val MORNING = listOf(
        key(-38.0, 0x0e1226, 0x161c38, 0x262a4c), key(-14.0, 0x161b34, 0x232b52, 0x3a3158),
        key(-8.0, 0x2c3568, 0x44558f, 0x8c6c86), key(-3.0, 0x34488a, 0x5878bd, 0xd69f78),
        key(1.0, 0x4a74c4, 0x7fa8e0, 0xf2cf8c), key(5.0, 0x5c93e0, 0x92bdf2, 0xcfe2f0),
        key(12.0, 0x4a8fe4, 0x7db5f2, 0xa9d4f7), key(26.0, 0x3d84d7, 0x5a9fe9, 0x8ec2f8),
    )

    private val EVENING = listOf(
        key(-38.0, 0x0e1226, 0x161c38, 0x262a4c), key(-14.0, 0x161b34, 0x2a2548, 0x523047),
        key(-8.0, 0x1d2142, 0x4a3560, 0xa2503f), key(-3.0, 0x2d3470, 0x7a4f86, 0xe07a56),
        key(1.0, 0x5f78cf, 0xb394b8, 0xf0b078), key(5.0, 0x5f86dc, 0x9aa4d8, 0xf0c8a0),
        key(12.0, 0x4f8ae0, 0x80afe8, 0xb3d2ee), key(26.0, 0x3d84d7, 0x5a9fe9, 0x8ec2f8),
    )

    private fun base(table: List<Key>, e: Double): Veil {
        val first = table.first()
        if (e <= first.elevation) return Veil(first.top, first.mid, first.hor)
        for (i in 1 until table.size) {
            val a = table[i - 1]
            val b = table[i]
            if (e <= b.elevation) {
                val t = smooth(a.elevation, b.elevation, e)
                return Veil(mix(a.top, b.top, t), mix(a.mid, b.mid, t), mix(a.hor, b.hor, t))
            }
        }
        val last = table.last()
        return Veil(last.top, last.mid, last.hor)
    }

    /** Пасмурность: цвет сереет (по яркости) и темнеет. */
    private fun tint(c: Rgb, gray: Double, dark: Double): Rgb {
        val l = c.r * 0.3 + c.g * 0.59 + c.b * 0.11
        val m = mix(c, Rgb(l, l, l), gray)
        return Rgb(m.r * (1 - dark), m.g * (1 - dark), m.b * (1 - dark))
    }

    /**
     * Цвета неба для высоты солнца [elevation]; [evening] от 0 (утро) до 1 (вечер): плавный переход
     * между утренней и вечерней таблицами около полудня, чтобы цвет не прыгал.
     */
    fun veil(elevation: Double, evening: Double, weather: SkyWeather): Veil {
        val m = base(MORNING, elevation)
        val v = base(EVENING, elevation)
        val b = Veil(mix(m.top, v.top, evening), mix(m.mid, v.mid, evening), mix(m.hor, v.hor, evening))
        val w = weather
        val day = smooth(-7.0, 6.0, elevation)
        var top = tint(b.top, w.gray, w.dark)
        var mid = tint(b.mid, w.gray * 0.9, w.dark * 0.9)
        var hor = tint(b.hor, w.gray * 0.85, w.dark * 0.75)
        top = mix(top, mid, w.gray * 0.3)
        mid = mix(mid, hor, w.gray * 0.25)
        if (w.fog > 0) {
            val fog = mix(mix(Rgb(46.0, 54.0, 68.0), Rgb(208.0, 214.0, 220.0), day), hor, 0.22)
            top = mix(top, fog, w.fog * 0.55)
            mid = mix(mid, fog, w.fog * 0.7)
            hor = mix(hor, fog, w.fog * 0.8)
        }
        return Veil(top, mid, hor)
    }
}
