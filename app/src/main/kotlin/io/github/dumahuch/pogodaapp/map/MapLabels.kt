package io.github.dumahuch.pogodaapp.map

/**
 * Какие подписи подложки basemap.de остаются на карте. В стиле их сотни слоёв (дороги и их номера, воды, леса,
 * ландшафты, острова, земли, башни, мачты, номера домов), для погоды нужны только названия городов и общин.
 * Остальные подписи скрываются; сами слои стиля остаются, чтобы наши слои вставали под первым из них.
 */
object MapLabels {

    /** Слои подписей, которые остаются: земельные столицы, города и общины. */
    private val KeptPrefixes = listOf("Name_Landeshauptstadt", "Name_Stadtgemeinde", "Name_Landgemeinde")

    fun isKept(layerId: String): Boolean = KeptPrefixes.any { layerId.startsWith(it) }

    /**
     * Какие из слоёв подписей [symbolLayerIds] скрыть. Если среди них нет ни одной оставляемой, не скрывается ничего:
     * иначе при смене названий слоёв в стиле на карте не осталось бы вообще ни одной подписи.
     */
    fun hidden(symbolLayerIds: List<String>): List<String> =
        if (symbolLayerIds.none(::isKept)) emptyList() else symbolLayerIds.filterNot(::isKept)
}
