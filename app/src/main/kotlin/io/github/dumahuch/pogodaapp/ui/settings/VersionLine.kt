package io.github.dumahuch.pogodaapp.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText

/** Номер версии для экрана «О приложении» и действие скрытого входа на экран проверки неба (семь нажатий подряд). */
class VersionEntry(val label: String, val onTap: () -> Unit)

/**
 * Последняя строка «О приложении»: «Версия 0.8.111 (8111)». Семь быстрых нажатий подряд (не реже раза в 1,5 секунды)
 * открывают экран проверки неба, как номер сборки в системных настройках Android. Для чтения с экрана это обычный текст.
 */
@Composable
internal fun VersionLine(version: VersionEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(version) { detectTapGestures { version.onTap() } }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.about_version_title), style = pogodaText(16f, 500))
        Text(version.label, style = pogodaText(13f, 400, color = LocalPogodaPalette.current.ink2))
    }
}
