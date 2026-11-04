package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.home.HeroFit
import io.github.dumahuch.pogodaapp.home.HomeUiState
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Наименьший промежуток над и под текущей погодой, когда свободного места почти нет (крупный шрифт системы). */
internal val CENTER_EDGE = 12.dp

/** Промежуток между строками под температурой и первой плашкой предупреждения. */
private val ALERTS_LEAD = 16.dp

/** Сколько длится рост блока и сдвиг при появлении или исчезновении предупреждения. */
private const val GROW_MS = 300

/** Свободная высота меняется меньше чем на столько пикселей: не пересчитываем (иначе округление качало бы план). */
private const val FREE_HYSTERESIS_PX = 3

/**
 * Замеры «Главной» с прошлой раскладки (пиксели): по ним считается свободная высота без предупреждений и без роста
 * блока. Высоты меняются, пока идёт анимация, но свободная высота от этого не зависит: что прибавилось одному,
 * ушло у другого, поэтому план не качается.
 */
internal class HomeLayout {
    var column by mutableIntStateOf(-1)
    var top by mutableIntStateOf(-1)
    var bottom by mutableIntStateOf(-1)
    var alerts by mutableIntStateOf(-1)
    var hero by mutableIntStateOf(-1)
    var row by mutableIntStateOf(-1)
    var more by mutableIntStateOf(-1)

    private var lastFree = Int.MIN_VALUE

    /** Замеры середины забываются, когда она уходит с экрана (загрузка, другое место): иначе первый кадр взял бы чужие. */
    fun forgetMiddle() {
        top = -1
        bottom = -1
        alerts = -1
        hero = -1
        lastFree = Int.MIN_VALUE
    }

    /**
     * Свободная высота при обычном размере блока и без предупреждений, вместе с двумя наименьшими промежутками:
     * высота окна минус всё, что не промежутки, не предупреждения и не рост блока. Пусто, пока замеров нет.
     */
    fun free(viewport: Int, heroBase: Float, edge: Int): Int? {
        if (column < 0 || top < 0 || bottom < 0 || alerts < 0 || hero < 0) return null
        val measured = viewport - column + top + bottom + edge + alerts + (hero - heroBase).roundToInt()
        if (lastFree == Int.MIN_VALUE || abs(measured - lastFree) >= FREE_HYSTERESIS_PX) lastFree = measured
        return lastFree
    }
}

/**
 * Середина «Главной» с данными: промежуток сверху, блок погоды (значок, температура, строки под ней), предупреждения и
 * всё оставшееся место снизу (до ленты «По часам»). Сколько плашек видно и насколько вырастет или сожмётся блок, считает [HeroFit];
 * изменения идут плавно ([GROW_MS]). На первой раскладке, пока замеров нет, блок стоит посередине, как в 0.5.4.
 */
@Composable
internal fun ColumnScope.ContentMiddle(
    state: HomeUiState.Content,
    layout: HomeLayout,
    viewport: Dp,
    pulled: Modifier,
    actions: HomeActions,
) {
    val density = LocalDensity.current
    val now = remember(state) { Instant.now() }
    val alerts = remember(state.alerts, now) { state.alerts?.shownAt(now).orEmpty() }
    val heroBase = remember(density) { density.heroBaseHeight() }
    val edge = with(density) { CENTER_EDGE.roundToPx() }
    val rows = with(density) {
        HeroFit.Rows(
            row = layout.row.takeIf { it > 0 }?.toFloat() ?: AlertRowHeight.toPx(),
            more = layout.more.takeIf { it > 0 }?.toFloat() ?: AlertMoreHeight.toPx(),
            gap = AlertGap.toPx(),
            lead = ALERTS_LEAD.toPx(),
        )
    }
    val free = layout.free(with(density) { viewport.roundToPx() }, heroBase, edge)
    val plan = free?.let { HeroFit.plan(it.toFloat(), alerts.size, rows, edge.toFloat(), heroBase) }

    DisposableEffect(layout) { onDispose { layout.forgetMiddle() } }
    val scale = remember { Animatable(1f) }
    val top = remember { Animatable(-1f) }
    LaunchedEffect(plan?.scale, plan?.top) {
        if (plan == null) return@LaunchedEffect
        if (top.value < 0f) {
            scale.snapTo(plan.scale)
            top.snapTo(plan.top)
        } else {
            launch { scale.animateTo(plan.scale, tween(GROW_MS)) }
            top.animateTo(plan.top, tween(GROW_MS))
        }
    }

    val topModifier = Modifier.onSizeChanged { layout.top = it.height }
    if (top.value < 0f) {
        // Первая раскладка: промежуток растягивается, как нижний; замер даёт свободную высоту, дальше он задан планом.
        Spacer(Modifier.weight(1f).heightIn(min = CENTER_EDGE).then(topModifier))
    } else {
        Spacer(Modifier.height(with(density) { top.value.toDp() }).then(topModifier))
    }
    Column(Modifier.fillMaxWidth().then(pulled)) {
        ContentTop(state, scale.value, Modifier.onSizeChanged { layout.hero = it.height })
    }
    val shown = plan?.shown ?: alerts.size
    Column(
        Modifier
            .fillMaxWidth()
            .then(pulled)
            .animateContentSize(tween(GROW_MS))
            .onSizeChanged { layout.alerts = it.height },
    ) {
        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(ALERTS_LEAD))
            AlertsList(
                alerts = alerts,
                shown = shown,
                more = plan?.more ?: false,
                now = now,
                zone = state.forecast.zone,
                onOpen = { actions.onOpenAlert(it.id) },
                onAll = actions.onOpenAlerts,
                onRowHeight = { layout.row = it },
                onMoreHeight = { layout.more = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
    Spacer(Modifier.height(CENTER_EDGE))
    Spacer(Modifier.weight(1f).onSizeChanged { layout.bottom = it.height })
}
