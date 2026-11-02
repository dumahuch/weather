package io.github.dumahuch.pogodaapp.ui.sky

import androidx.lifecycle.ViewModel
import io.github.dumahuch.pogodaapp.home.PollenStatus
import io.github.dumahuch.pogodaapp.sky.SkyOverride
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import io.github.dumahuch.pogodaapp.weather.PollenKind
import io.github.dumahuch.pogodaapp.weather.PollenLevel
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

    /** Вместо настоящей пыльцы на плитке «Пыльца» подставлен пример: вне сезона видно, как плитка выглядит с данными. */
    val pollenSample: StateFlow<Boolean> = mutablePollenSample.asStateFlow()

    fun show() {
        mutableOpen.value = true
    }

    /** При закрытии небо возвращается к настоящему времени и погоде, размытие включается, пример пыльцы убирается. */
    fun close() {
        mutableOpen.value = false
        mutableOverride.value = SkyOverride()
        mutableGlassBlur.value = true
        mutablePollenSample.value = false
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
