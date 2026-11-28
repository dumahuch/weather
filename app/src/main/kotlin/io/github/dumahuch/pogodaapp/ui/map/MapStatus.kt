package io.github.dumahuch.pogodaapp.ui.map

/** Что сказать строкой состояния карты (под временем). Текст по значению берёт экран из ресурсов. */
enum class MapStatus(val retryable: Boolean = false) {
    BaseFailed,
    NoConnection(retryable = true),
    RadarFailed(retryable = true),
    CloudsFailed(retryable = true),
    NoData,
    NoClouds,
    Loading,
}

/**
 * Строка состояния карты по загрузке кадров; null, когда сказать нечего. Если не ответила только часть данных слоя (радар
 * при загруженных облаках), так и говорится, а не «нет связи»; полное «нет связи» только когда не ответило всё, что слою нужно.
 * Сбой, который можно повторить касанием строки, помечен [MapStatus.retryable].
 */
fun mapStatus(state: MapUiState, baseFailed: Boolean): MapStatus? {
    if (state.layer == MapLayer.Temperature || state.layer == MapLayer.Wind) {
        return when {
            baseFailed -> MapStatus.BaseFailed
            state.field == FrameStatus.Failed -> MapStatus.NoConnection
            state.field == FrameStatus.Missing -> MapStatus.NoData
            state.framesDone < state.framesTotal -> MapStatus.Loading
            else -> null
        }
    }
    val withClouds = state.layer == MapLayer.CloudsAndRain
    val clouds = if (withClouds) state.clouds else FrameStatus.Ready
    val rainFailed = state.rain == FrameStatus.Failed
    val cloudsFailed = clouds == FrameStatus.Failed
    return when {
        baseFailed -> MapStatus.BaseFailed
        rainFailed && cloudsFailed -> MapStatus.NoConnection
        rainFailed -> if (withClouds) MapStatus.RadarFailed else MapStatus.NoConnection
        cloudsFailed -> MapStatus.CloudsFailed
        state.rain == FrameStatus.Missing && clouds != FrameStatus.Ready -> MapStatus.NoData
        clouds == FrameStatus.Missing -> MapStatus.NoClouds
        state.framesDone < state.framesTotal -> MapStatus.Loading
        else -> null
    }
}
