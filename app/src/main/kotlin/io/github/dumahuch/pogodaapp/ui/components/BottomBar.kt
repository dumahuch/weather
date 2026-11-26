package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Промежуток между панелью вкладок и системной панелью жестов. */
private val DockBottomGap = 12.dp

/**
 * Сколько места снизу занимает нижняя часть окна (панель вкладок) без отступа системной панели.
 * Содержимое страниц оставляет столько же свободного места внизу, чтобы прокрутка доходила до конца.
 */
val BottomBarHeight: Dp = DockHeight + DockBottomGap

/** Отступ снизу для содержимого страницы: нижняя часть окна, системная панель жестов и небольшой запас. */
@Composable
fun bottomContentPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BottomBarHeight + 6.dp

/**
 * Нижняя часть окна: панель вкладок. Строки источника данных внизу больше нет (0.8.111): источники и лицензии лежат
 * в «Настройки → О приложении», там же номер версии и скрытый вход на экран проверки неба.
 */
@Composable
fun BottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.navigationBarsPadding().padding(bottom = DockBottomGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dock(selected = selected, onSelect = onSelect)
    }
}
