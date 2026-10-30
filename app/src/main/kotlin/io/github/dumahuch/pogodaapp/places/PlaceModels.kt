package io.github.dumahuch.pogodaapp.places

import io.github.dumahuch.pogodaapp.location.GeoPoint
import io.github.dumahuch.pogodaapp.location.distanceKm
import java.util.Locale

/**
 * Подсказка поиска города: название, область, страна и координаты. Область и страна подписаны на языке интерфейса,
 * если у службы поиска есть перевод (иначе английское или местное название).
 */
data class PlaceSuggestion(
    val name: String,
    val admin1: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
)

/** Закреплённое место. [id] выводится из координат ([placeId]), по нему лежит файл с сохранённым прогнозом. */
data class PinnedPlace(
    val id: String,
    val name: String,
    val admin1: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)

    companion object {
        fun of(suggestion: PlaceSuggestion): PinnedPlace = PinnedPlace(
            id = placeId(suggestion.latitude, suggestion.longitude),
            name = suggestion.name,
            admin1 = suggestion.admin1,
            country = suggestion.country,
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
        )

        /** Место, найденное по геолокации: службе адресов известно только название. */
        fun ofPoint(point: GeoPoint, name: String): PinnedPlace = PinnedPlace(
            id = placeId(point.latitude, point.longitude),
            name = name,
            admin1 = null,
            country = null,
            latitude = point.latitude,
            longitude = point.longitude,
        )
    }
}

/** Какое место показывает приложение: то, где телефон сейчас, или одно из закреплённых. */
sealed interface PlaceChoice {
    data object Current : PlaceChoice
    data class Pinned(val id: String) : PlaceChoice
}

/** Чем кончилась попытка закрепить место. */
enum class PinResult {
    /** Место добавлено в список. */
    Added,

    /** Такое место (в пределах [PlacesLogic.SAME_PLACE_KM]) уже закреплено, показывается оно. */
    AlreadyPinned,

    /** В списке уже [PlacesLogic.MAX_PINNED] мест. */
    ListFull,
}

/** Сообщение на экране мест. Текст подбирает экран (на трёх языках). */
enum class PlacesMessage {
    /** Список полон: чтобы добавить место, надо открепить одно из пяти. */
    ListFull,

    /** Телефон определяет, где он. */
    Locating,

    /** Место определить не удалось (геолокация выключена, нет сигнала или нет доступа). */
    LocateFailed,
}

object PlacesLogic {
    /** Сколько мест можно закрепить (решение Димы 30.10.2026). */
    const val MAX_PINNED = 5

    /** Места ближе этого расстояния считаются одним и тем же (как в остальном приложении). */
    const val SAME_PLACE_KM = 2.0

    /** Короче запрос не отправляется: служба поиска на один знак ничего не отвечает, на два ищет только точное имя. */
    const val MIN_QUERY_LENGTH = 2

    /**
     * Подсказки в порядке показа: сначала места страны, где находится телефон ([homeCountryCode]), потом остальные.
     * Внутри каждой группы остаётся порядок службы поиска (по значимости). Одинаковые места (в пределах километра)
     * показываются один раз.
     */
    fun rank(suggestions: List<PlaceSuggestion>, homeCountryCode: String?): List<PlaceSuggestion> {
        val unique = ArrayList<PlaceSuggestion>()
        for (item in suggestions) {
            val duplicate = unique.any {
                it.name == item.name && distanceKm(GeoPoint(it.latitude, it.longitude), GeoPoint(item.latitude, item.longitude)) < 1.0
            }
            if (!duplicate) unique += item
        }
        val home = homeCountryCode?.takeIf { it.isNotBlank() }
        if (home == null) return unique
        return unique.sortedBy { if (it.countryCode.equals(home, ignoreCase = true)) 0 else 1 }
    }

    /** Закреплённое место, которое совпадает с точкой (в пределах [SAME_PLACE_KM]), если есть. */
    fun findSame(pinned: List<PinnedPlace>, latitude: Double, longitude: Double): PinnedPlace? {
        val point = GeoPoint(latitude, longitude)
        return pinned.firstOrNull { distanceKm(it.point, point) < SAME_PLACE_KM }
    }
}

/** Подпись из названия, области и страны через « · ». Пустые и повторяющие название части пропускаются. */
fun placeLabel(name: String, admin1: String?, country: String?): String =
    listOfNotNull(name, admin1, country)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .fold(ArrayList<String>()) { parts, part -> if (part !in parts) parts += part; parts }
        .joinToString(" · ")

/** Область и страна без названия: вторая строка подсказки. Пусто, если ни того ни другого нет. */
fun placeRegionLine(name: String, admin1: String?, country: String?): String =
    listOfNotNull(admin1, country)
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != name.trim() }
        .distinct()
        .joinToString(" · ")

/** Устойчивый номер места по координатам, округлённым до сотых долей градуса: «52.37_9.74». */
fun placeId(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.2f_%.2f", latitude, longitude)
