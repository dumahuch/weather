package io.github.dumahuch.pogodaapp.sky

// Создан design/sky/di/tools/make_di.py вместе с листами облаков: руками не править.

/** Где на листах облаков фона «Ди» лежит каждое облако (пиксели листа 2048×768), порядок как у файлов в design/sky/di/clouds. */
internal object CloudSprites {
    class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    val light = listOf(
        Rect(1116, 32, 487, 270),
        Rect(1502, 354, 220, 136),
        Rect(598, 32, 486, 274),
        Rect(32, 32, 534, 290),
        Rect(459, 354, 356, 205),
        Rect(847, 354, 374, 199),
        Rect(32, 354, 395, 213),
        Rect(1253, 354, 217, 139),
    )

    val storm = listOf(
        Rect(32, 32, 560, 309),
        Rect(1268, 32, 553, 302),
        Rect(840, 373, 318, 218),
        Rect(624, 32, 612, 304),
        Rect(32, 373, 369, 238),
        Rect(433, 373, 375, 230),
    )
}
