package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sky.rememberAnimationsEnabled

/** Вкладки приложения в порядке нижней панели. */
enum class AppTab(val label: Int, val icon: LineIcon, val enabled: Boolean) {
    Home(R.string.tab_home, LineIcons.Home, enabled = true),
    Details(R.string.tab_details, LineIcons.Grid, enabled = true),
    Forecast(R.string.tab_forecast, LineIcons.Calendar, enabled = true),
    Map(R.string.tab_map, LineIcons.Map, enabled = true),
}

/** Высота нижней панели (кнопка 48 и по 6 отступа). */
val DockHeight = 60.dp

/**
 * Нижняя панель с четырьмя вкладками. Вкладка, которая ещё не сделана («Карта»),
 * приглушена и недоступна.
 */
@Composable
fun Dock(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Row(
        modifier = modifier
            .glassButton(RoundedCornerShape(34.dp), glass)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppTab.entries.forEach { tab -> DockButton(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
    }
}

@Composable
private fun DockButton(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(tab.label)
    val palette = LocalPogodaPalette.current
    val shape = RoundedCornerShape(24.dp)
    // Выбранная вкладка не прыгает, а перетекает: плашка проявляется, подпись выезжает, значок темнеет.
    val animations by rememberAnimationsEnabled()
    val change = if (animations) tween<Float>(DOCK_MS) else snap<Float>()
    val on by animateFloatAsState(if (selected) 1f else 0f, change, label = "dockTab")
    val labelSize = if (animations) tween<IntSize>(DOCK_MS) else snap<IntSize>()
    Row(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .drawBehind { drawRect(palette.buttonOn, alpha = on) }
            .alpha(if (tab.enabled) 1f else 0.45f)
            .selectable(selected = selected, enabled = tab.enabled, role = Role.Tab, onClick = onClick)
            .then(if (selected) Modifier else Modifier.semantics { contentDescription = label })
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        LineIconView(tab.icon, 24.dp, lerp(palette.ink2, palette.ink, on))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(labelSize) + fadeIn(change),
            exit = shrinkHorizontally(labelSize) + fadeOut(change),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = pogodaText(14f, 600), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Столько же, сколько въезжает новая вкладка (`PUSH_MS`): плашка и содержимое заканчивают вместе. */
private const val DOCK_MS = 320
