package io.github.dumahuch.pogodaapp.ui.components

/**
 * Линейные значки 24×24 (набор Lucide, лицензия ISC, см. third_party/LICENSE-Lucide.txt).
 * Контуры взяты из макета без изменений; круги и прямоугольники записаны как контуры.
 */
class LineIcon(val paths: List<String>)

object LineIcons {
    val Sun = LineIcon(
        listOf(
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
            "M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41",
        ),
    )

    val Moon = LineIcon(
        listOf(
            "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z",
        ),
    )

    val Cloud = LineIcon(
        listOf(
            "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
        ),
    )

    val PartlyCloudy = LineIcon(
        listOf(
            "M12 2v2M4.93 4.93l1.41 1.41M20 12h2M19.07 4.93l-1.41 1.41M15.947 12.65a4 4 0 0 0-5.925-4.128M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z",
        ),
    )

    val PartlyCloudyNight = LineIcon(
        listOf(
            "M10.188 8.5A6 6 0 0 1 16 4a1 1 0 0 0 6 6 6 6 0 0 1-3 5.197M13 16a3 3 0 1 1 0 6H7a5 5 0 1 1 4.9-6Z",
        ),
    )

    val Rain = LineIcon(
        listOf(
            "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M16 14v6M8 14v6M12 16v6",
        ),
    )

    val Storm = LineIcon(
        listOf(
            "M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973M13 12l-3 5h4l-3 5",
        ),
    )

    val Snow = LineIcon(
        listOf(
            "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M8 15h.01M8 19h.01M12 17h.01M12 21h.01M16 15h.01M16 19h.01",
        ),
    )

    val Fog = LineIcon(
        listOf(
            "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M16 17H7M17 21H9",
        ),
    )

    val Navigation = LineIcon(
        listOf(
            "M3 11 22 2l-9 19-2-8z",
        ),
    )

    val Pin = LineIcon(
        listOf(
            "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z",
            "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        ),
    )

    // Контуры CloudOff и NavigationOff в макете не было: они взяты из набора Lucide дословно (cloud-off.svg, navigation-off.svg).
    val CloudOff = LineIcon(
        listOf(
            "M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057",
            "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78",
            "m2 2 20 20",
        ),
    )

    val NavigationOff = LineIcon(
        listOf(
            "M8.43 8.43 3 11l8 2 2 8 2.57-5.43",
            "M17.39 11.73 22 2l-9.73 4.61",
            "m2 2 20 20",
        ),
    )

    /** Стрелка «назад» (Lucide chevron-left). */
    val ChevronLeft = LineIcon(
        listOf(
            "m15 18-6-6 6-6",
        ),
    )

    /** Стрелка вниз у плашки места (Lucide chevron-down): плашка открывает список мест. */
    val ChevronDown = LineIcon(
        listOf(
            "m6 9 6 6 6-6",
        ),
    )

    /** Лупа (Lucide search); круг записан как контур. */
    val Search = LineIcon(
        listOf(
            "m21 21-4.34-4.34",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z",
        ),
    )

    /** Крестик (Lucide x). */
    val Close = LineIcon(
        listOf(
            "M18 6 6 18",
            "m6 6 12 12",
        ),
    )

    /** Галочка (Lucide check). */
    val Check = LineIcon(
        listOf(
            "M20 6 9 17l-5-5",
        ),
    )

    /** Прицел (Lucide locate-fixed): «определить место»; круги записаны как контуры. */
    val Locate = LineIcon(
        listOf(
            "M2 12h3M19 12h3M12 2v3M12 19v3",
            "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        ),
    )

    val Home = LineIcon(
        listOf(
            "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z",
        ),
    )

    val Calendar = LineIcon(
        listOf(
            "M5.5 4.5h13a2.5 2.5 0 0 1 2.5 2.5v11.5a2.5 2.5 0 0 1 -2.5 2.5h-13a2.5 2.5 0 0 1 -2.5 -2.5v-11.5a2.5 2.5 0 0 1 2.5 -2.5z",
            "M3 9.5h18M8 2.5v4M16 2.5v4M7.5 13.5h2M11 13.5h2M14.5 13.5h2M7.5 17h2M11 17h2",
        ),
    )

    val Grid = LineIcon(
        listOf(
            "M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1 -1.5 1.5h-4a1.5 1.5 0 0 1 -1.5 -1.5v-4a1.5 1.5 0 0 1 1.5 -1.5z",
            "M15.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1 -1.5 1.5h-4a1.5 1.5 0 0 1 -1.5 -1.5v-4a1.5 1.5 0 0 1 1.5 -1.5z",
            "M15.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1 -1.5 1.5h-4a1.5 1.5 0 0 1 -1.5 -1.5v-4a1.5 1.5 0 0 1 1.5 -1.5z",
            "M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1 -1.5 1.5h-4a1.5 1.5 0 0 1 -1.5 -1.5v-4a1.5 1.5 0 0 1 1.5 -1.5z",
        ),
    )

    val Map = LineIcon(
        listOf(
            "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0zM15 5.764v15M9 3.236v15",
        ),
    )
}
