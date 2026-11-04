package io.github.dumahuch.pogodaapp.ui.sky

import androidx.lifecycle.ViewModel
import io.github.dumahuch.pogodaapp.home.PollenStatus
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.AlertSet
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Состояние экрана проверки: открыт ли он и что подставлено вместо настоящих времени и погоды. */
internal class SkyCheckModel : ViewModel() {
    private val mutableOpen = MutableStateFlow(false)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

    private val mutableOverride = MutableStateFlow(SkyOverride())
    val override: StateFlow<SkyOverride> = mutableOverride.asStateFlow()

    private val mutableGlassBlur = MutableStateFlow(true)

    /** Размытие под стеклом включено. Выключатель нужен, чтобы сравнить кадры на телефоне с размытием и без. */
    val glassBlur: StateFlow<Boolean> = mutableGlassBlur.asStateFlow()

    private val mutablePollenSample = MutableStateFlow(false)

    private val mutableAlertSample = MutableStateFlow(0)

    /**
     * Сколько учебных предупреждений подставить на «Главную» вместо настоящих (0: настоящие). Нужно, чтобы без непогоды
     * увидеть плашки, строку «Ещё N», шторку и как блок погоды уступает им место.
     */
    val alertSample: StateFlow<Int> = mutableAlertSample.asStateFlow()

    /** Вместо настоящей пыльцы на плитке «Пыльца» подставлен пример: вне сезона видно, как плитка выглядит с данными. */
    val pollenSample: StateFlow<Boolean> = mutablePollenSample.asStateFlow()

    fun show() {
        mutableOpen.value = true
    }

    /** При закрытии небо возвращается к настоящему времени и погоде, размытие включается, примеры пыльцы и предупреждений убираются. */
    fun close() {
        mutableOpen.value = false
        mutableOverride.value = SkyOverride()
        mutableGlassBlur.value = true
        mutablePollenSample.value = false
        mutableAlertSample.value = 0
    }

    fun setAlertSample(count: Int) {
        mutableAlertSample.value = count.coerceIn(0, SAMPLE_ALERTS)
    }

    fun setPollenSample(on: Boolean) {
        mutablePollenSample.value = on
    }

    fun setGlassBlur(on: Boolean) {
        mutableGlassBlur.value = on
    }

    fun set(value: SkyOverride) {
        mutableOverride.value = value
    }
}

/**
 * Сведения о пыльце для экрана проверки: чем кончилась загрузка ([status]), что получено ([forecast], пусто, если
 * ничего) и какой день считать сегодняшним ([today], по часовому поясу места).
 */
internal class PollenCheck(val status: PollenStatus, val forecast: PollenForecast?, val today: LocalDate)

/** Пример для кнопки «Подставить пыльцу»: четыре вида на разных ступенях, выпуск сегодняшний. Не настоящие данные. */
internal fun samplePollen(today: LocalDate): PollenForecast = PollenForecast(
    regionKey = SAMPLE_REGION_KEY,
    issued = today,
    levels = mapOf(
        PollenKind.Birch to listOf(PollenLevel(5), PollenLevel(4), PollenLevel(2)),
        PollenKind.Grass to listOf(PollenLevel(3), PollenLevel(3), PollenLevel(2)),
        PollenKind.Mugwort to listOf(PollenLevel(1), PollenLevel(2), PollenLevel(2)),
        PollenKind.Hazel to listOf(PollenLevel(6), PollenLevel(5), PollenLevel(4)),
    ),
)

private const val SAMPLE_REGION_KEY = 0

/** Сколько всего учебных предупреждений есть. */
internal const val SAMPLE_ALERTS = 6

/**
 * Учебные предупреждения для экрана проверки: все четыре уровня, начавшиеся и будущие, с концом сегодня и завтра.
 * В тексте каждого прямо сказано, что это не настоящее предупреждение. Не настоящие данные.
 */
internal fun sampleAlerts(count: Int, now: Instant): AlertSet {
    fun at(hours: Double): Instant = now.plus(Duration.ofMinutes((hours * 60).toLong()))
    fun sample(index: Int, level: AlertLevel, kind: AlertKind, de: String, en: String, from: Double, to: Double?) = WeatherAlert(
        id = "sample-$index",
        level = level,
        kind = kind,
        onset = at(from),
        expires = to?.let(::at),
        issued = at(-1.5),
        headlineDe = "ÜBUNG: keine echte Warnung ($de)",
        headlineEn = "PRACTICE: not a real warning ($en)",
        descriptionDe = "Diese Warnung ist ein Beispiel für die Prüfansicht von PogodaApp. Es besteht keine Gefahr.",
        descriptionEn = "This warning is a sample for the PogodaApp check screen. There is no danger.",
        instructionDe = if (index == 1) "Hinweis: Echte Warnungen kommen vom Deutschen Wetterdienst." else null,
        instructionEn = if (index == 1) "Note: real warnings come from the Deutscher Wetterdienst." else null,
    )
    val all = listOf(
        sample(1, AlertLevel.Extreme, AlertKind.Wind, "Orkanböen", "hurricane-force gusts", -1.0, 5.0),
        sample(2, AlertLevel.Severe, AlertKind.Thunderstorm, "schweres Gewitter", "severe thunderstorm", 0.5, 4.0),
        sample(3, AlertLevel.Moderate, AlertKind.Rain, "Dauerregen", "persistent rain", 3.0, 27.0),
        sample(4, AlertLevel.Minor, AlertKind.Frost, "Frost", "frost", 8.0, 18.0),
        sample(5, AlertLevel.Minor, AlertKind.Fog, "Nebel", "fog", -2.0, 2.0),
        sample(6, AlertLevel.Moderate, AlertKind.Snow, "Schneefall", "snowfall", 30.0, null),
    )
    return AlertSet(area = "Übungsgebiet (nicht echt)", alerts = all.take(count.coerceIn(0, SAMPLE_ALERTS)))
}
