package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText

private class DockTab(val label: Int, val icon: LineIcon, val enabled: Boolean)

private val Tabs = listOf(
    DockTab(R.string.tab_home, LineIcons.Home, enabled = true),
    DockTab(R.string.tab_forecast, LineIcons.Calendar, enabled = false),
    DockTab(R.string.tab_details, LineIcons.Grid, enabled = false),
    DockTab(R.string.tab_map, LineIcons.Map, enabled = false),
)

/** Высота нижней панели (кнопка 48 и по 6 отступа). */
val DockHeight = 60.dp

/**
 * Нижняя панель с четырьмя вкладками. Пока работает только «Главная»: остальные вкладки
 * приглушены и недоступны, пока не будут сделаны.
 */
@Composable
fun Dock(modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Row(
        modifier = modifier
            .glassButton(RoundedCornerShape(34.dp), palette)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tabs.forEachIndexed { index, tab -> DockButton(tab, selected = index == 0) }
    }
}

@Composable
private fun DockButton(tab: DockTab, selected: Boolean) {
    val label = stringResource(tab.label)
    val palette = LocalPogodaPalette.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(palette.buttonOn, shape) else Modifier)
            .alpha(if (tab.enabled) 1f else 0.45f)
            .selectable(selected = selected, enabled = tab.enabled, role = Role.Tab, onClick = {})
            .then(if (selected) Modifier else Modifier.semantics { contentDescription = label })
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        LineIconView(tab.icon, 24.dp, if (selected) palette.ink else palette.ink2)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = pogodaText(14f, 600))
        }
    }
}
