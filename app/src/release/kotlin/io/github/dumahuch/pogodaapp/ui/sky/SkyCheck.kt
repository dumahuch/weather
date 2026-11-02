package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.dumahuch.pogodaapp.sky.SkyOverride

/**
 * В выпускной сборке экрана проверки неба нет: это рабочий инструмент отладочной сборки (там же `TapSequence`,
 * `FrameStats` и сам экран). Здесь семь нажатий на номер версии ничего не открывают, панель не рисуется.
 */
internal class TapSequence {
    @Suppress("UNUSED_PARAMETER")
    fun tap(nowMillis: Long): Boolean = false
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SkyCheckPanel(
    input: SkyInput?,
    override: SkyOverride,
    diagnostics: SkyDiagnostics,
    glassBlur: Boolean,
    pollen: PollenCheck?,
    pollenSample: Boolean,
    onOverride: (SkyOverride) -> Unit,
    onGlassBlur: (Boolean) -> Unit,
    onPollenSample: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
