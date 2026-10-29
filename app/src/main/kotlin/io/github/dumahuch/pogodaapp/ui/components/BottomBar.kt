package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText

private const val OPEN_METEO_URL = "https://open-meteo.com/"

private val DockBottomGap = 2.dp

/** Высота строки с источником данных: мелкий текст и по 8 dp вокруг для нажатия. */
private val FooterHeight = 32.dp

/**
 * Сколько места снизу занимает нижняя часть окна (панель вкладок и строка источника) без отступа системной панели.
 * Содержимое страниц оставляет столько же свободного места внизу, чтобы прокрутка доходила до конца.
 */
val BottomBarHeight: Dp = DockHeight + DockBottomGap + FooterHeight

/** Отступ снизу для содержимого страницы: нижняя часть окна, системная панель жестов и небольшой запас. */
@Composable
fun bottomContentPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BottomBarHeight + 6.dp

/**
 * Нижняя часть окна: панель вкладок и под ней самой нижней строкой источник данных с номером версии.
 * Ссылка на Open-Meteo нужна по лицензии рядом с данными (CC BY 4.0), поэтому показывается на всех вкладках, пока данные есть.
 */
@Composable
fun BottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    versionLabel: String,
    showAttribution: Boolean,
    onVersionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dock(selected = selected, onSelect = onSelect)
        SourceFooter(
            versionLabel = versionLabel,
            showAttribution = showAttribution,
            onVersionTap = onVersionTap,
            modifier = Modifier.padding(top = DockBottomGap),
        )
    }
}

/** Источник данных (условие лицензии Open-Meteo) и номер версии. */
@Composable
private fun SourceFooter(
    versionLabel: String,
    showAttribution: Boolean,
    onVersionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val style = pogodaText(11f, 500, color = LocalPogodaPalette.current.ink2)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAttribution) {
            Text(
                stringResource(R.string.attribution_open_meteo),
                style = style.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        try {
                            uriHandler.openUri(OPEN_METEO_URL)
                        } catch (e: IllegalArgumentException) {
                            // Нет приложения для открытия ссылок: ничего не делаем.
                        }
                    }
                    .padding(vertical = 8.dp),
            )
            Text(" · ", style = style)
        }
        // Скрытый вход на экран проверки неба: несколько нажатий подряд. Для чтения с экрана это обычный текст.
        Text(
            versionLabel,
            style = style,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .pointerInput(onVersionTap) { detectTapGestures { onVersionTap() } }
                .padding(vertical = 8.dp),
        )
    }
}
