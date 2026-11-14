package io.github.dumahuch.pogodaapp.ui.map

import android.net.ConnectivityManager
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.sentenceCase
import io.github.dumahuch.pogodaapp.map.MapDay
import io.github.dumahuch.pogodaapp.map.MapTimeline
import io.github.dumahuch.pogodaapp.map.RainPalette
import io.github.dumahuch.pogodaapp.map.TemperatureField
import io.github.dumahuch.pogodaapp.map.WindField
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.components.glassButton
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.Place
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Вкладка «Карта» (0.8.0): тёмная подложка basemap.de, облака ICON-D2 и дождь DWD, температура и ветер ICON-D2, метка
 * места. Слева сверху плашка с названием слоя. Над панелью времени слева легенда слоя, справа (от нижнего края вверх,
 * чтобы дотягиваться на больших экранах) кнопка «моё место», отделённая просветом, и над ней четыре слоя. Внизу стеклянная панель со
 * временем, сутками, кнопкой «играть» и часовой шкалой. Цвета свои, тёмные ([MapPalette]): их задаёт тот, кто
 * показывает экран.
 *
 * [skyDarkInk] нужен, чтобы при уходе с карты вернуть системным значкам цвет неба: на карте они всегда светлые.
 */
@Composable
fun MapScreen(place: Place?, skyDarkInk: Boolean, wifiOnly: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model: MapViewModel = viewModel {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        MapViewModel(
            userAgent = "PogodaApp/${AppVersion.read(context).name}",
            cacheRoot = mapCacheRoot(context),
            modelRoot = mapModelRoot(context),
            metered = { connectivity?.isActiveNetworkMetered ?: true },
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    val images by model.images.collectAsStateWithLifecycle()
    var locate by remember { mutableIntStateOf(0) }
    var baseFailed by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    // Настройка ставится до onShown: по ней решается, качать ли кадры суток заранее.
    SideEffect { model.setWifiOnly(wifiOnly) }
    DisposableEffect(model) {
        model.setWifiOnly(wifiOnly)
        model.onShown()
        MapCacheJobs.scheduleCleanup(context)
        onDispose { model.onHidden() }
    }
    LightBarIcons(restoreDark = skyDarkInk)

    Box(modifier.fillMaxSize().background(MapBackground)) {
        MapLibreHost(
            images = images,
            layer = state.layer,
            place = place,
            locateRequests = locate,
            onFailed = { baseFailed = true },
            modifier = Modifier.fillMaxSize(),
        )
        LayerName(
            layer = state.layer,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = bottomContentPadding()),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                MapLegend(state.layer, Modifier.padding(start = 6.dp, bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                MapButtons(
                    layer = state.layer,
                    canLocate = place != null,
                    onLocate = { locate++ },
                    onLayer = model::selectLayer,
                )
            }
            TimePanel(
                state = state,
                zone = zone,
                status = statusText(state, baseFailed),
                onDay = model::selectDay,
                onPlay = model::togglePlay,
                onTime = model::scrubTo,
                onSettled = model::scrubEnd,
            )
        }
    }
}

/** На тёмной карте значки строки состояния и панели жестов светлые; при уходе с карты их цвет снова как у неба. */
@Composable
private fun LightBarIcons(restoreDark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, restoreDark) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        view.windowInsetsController?.setSystemBarsAppearance(0, mask)
        onDispose { view.windowInsetsController?.setSystemBarsAppearance(if (restoreDark) mask else 0, mask) }
    }
}

@Composable
private fun statusText(state: MapUiState, baseFailed: Boolean): String? {
    if (state.layer == MapLayer.Temperature || state.layer == MapLayer.Wind) {
        return when {
            baseFailed -> stringResource(R.string.map_base_failed)
            state.field == FrameStatus.Failed -> stringResource(R.string.map_no_connection)
            state.field == FrameStatus.Missing -> stringResource(R.string.map_no_data)
            state.framesDone < state.framesTotal -> stringResource(R.string.map_loading_frames, state.framesDone, state.framesTotal)
            else -> null
        }
    }
    val clouds = if (state.layer == MapLayer.CloudsAndRain) state.clouds else FrameStatus.Ready
    return when {
        baseFailed -> stringResource(R.string.map_base_failed)
        state.rain == FrameStatus.Failed || clouds == FrameStatus.Failed -> stringResource(R.string.map_no_connection)
        state.rain == FrameStatus.Missing && clouds != FrameStatus.Ready -> stringResource(R.string.map_no_data)
        clouds == FrameStatus.Missing -> stringResource(R.string.map_no_clouds)
        state.framesDone < state.framesTotal -> stringResource(R.string.map_loading_frames, state.framesDone, state.framesTotal)
        else -> null
    }
}

/** Плашка с названием слоя, слева сверху. */
@Composable
private fun LayerName(layer: MapLayer, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Row(modifier.fillMaxWidth()) {
        Text(
            stringResource(layer.label()),
            style = pogodaText(12f, 600, color = palette.ink),
            modifier = Modifier
                .glassButton(RoundedCornerShape(14.dp), glass)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Кнопки справа над панелью времени: четыре слоя, под ними с просветом [LocateGap] кнопка «моё место», самая нижняя,
 * чтобы она выделялась и до неё было проще дотянуться.
 */
@Composable
private fun MapButtons(layer: MapLayer, canLocate: Boolean, onLocate: () -> Unit, onLayer: (MapLayer) -> Unit) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LayerButton(MapLayer.CloudsAndRain, LineIcons.Rain, selected = layer == MapLayer.CloudsAndRain, onLayer)
        Spacer(Modifier.height(8.dp))
        LayerButton(MapLayer.RainOnly, LineIcons.Drop, selected = layer == MapLayer.RainOnly, onLayer)
        Spacer(Modifier.height(8.dp))
        LayerButton(MapLayer.Temperature, LineIcons.Thermometer, selected = layer == MapLayer.Temperature, onLayer)
        Spacer(Modifier.height(8.dp))
        LayerButton(MapLayer.Wind, LineIcons.Wind, selected = layer == MapLayer.Wind, onLayer)
        Spacer(Modifier.height(LocateGap))
        val locateLabel = stringResource(R.string.map_locate)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = canLocate, role = Role.Button, onClick = onLocate)
                .semantics { contentDescription = locateLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).glassButton(CircleShape, glass), contentAlignment = Alignment.Center) {
                LineIconView(LineIcons.Locate, 18.dp, if (canLocate) palette.ink else palette.ink2)
            }
        }
    }
}

/** Просвет между кнопками слоёв и кнопкой «моё место». */
private val LocateGap = 16.dp

private fun MapLayer.label(): Int = when (this) {
    MapLayer.CloudsAndRain -> R.string.map_layer_clouds_rain
    MapLayer.RainOnly -> R.string.map_layer_rain
    MapLayer.Temperature -> R.string.map_layer_temperature
    MapLayer.Wind -> R.string.map_layer_wind
}

/** Кнопка слоя: видимый квадрат 40, нажимаемая область 48. */
@Composable
private fun LayerButton(layer: MapLayer, icon: LineIcon, selected: Boolean, onLayer: (MapLayer) -> Unit) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val label = stringResource(layer.label())
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onLayer(layer) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .glassButton(shape, glass)
                .then(if (selected) Modifier.background(palette.accentSoft, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            LineIconView(icon, 20.dp, if (selected) palette.ink else palette.ink2)
        }
    }
}

/** Легенда слоя: дождь, температура или ветер. */
@Composable
private fun MapLegend(layer: MapLayer, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    when (layer) {
        MapLayer.CloudsAndRain, MapLayer.RainOnly -> RainLegend(modifier)
        MapLayer.Temperature -> {
            val min = TemperatureField.LegendMin
            val max = TemperatureField.LegendMax
            val colors = remember {
                List(LEGEND_SAMPLES) { k -> Color(0xFF000000.toInt() or TemperatureField.colorOf(max - (max - min) * k / (LEGEND_SAMPLES - 1))) }
            }
            val marks = remember(number) {
                TemperatureField.LegendValues.map { number.format(it) to ((it - min) / (max - min)).toFloat() }
            }
            ScaleLegend(colors, marks, stringResource(R.string.map_legend_unit_temperature), LegendTallHeight, modifier)
        }
        MapLayer.Wind -> {
            val colors = remember {
                List(LEGEND_SAMPLES) { k -> Color(0xFF000000.toInt() or WindField.colorOf(WindField.LEGEND_MAX * (1 - k.toDouble() / (LEGEND_SAMPLES - 1)))) }
            }
            val marks = remember(number) { WindField.LegendValues.map { number.format(it) to (it / WindField.LEGEND_MAX).toFloat() } }
            ScaleLegend(colors, marks, stringResource(R.string.map_legend_unit_wind), LegendHeight, modifier)
        }
    }
}

/** Легенда дождя: полоса цвета от слабого (внизу) к сильному (вверху), отметки 0,1 / 1 / 5 / 20 / 100 мм/ч по логарифмической шкале. */
@Composable
private fun RainLegend(modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val colors = remember {
        List(LEGEND_SAMPLES) { k ->
            val position = 1.0 - k.toDouble() / (LEGEND_SAMPLES - 1)
            val mm = 0.1 * Math.pow(RainPalette.LegendValues.last() / RainPalette.LegendValues.first(), position)
            Color(0xFF000000.toInt() or RainPalette.legendColor(mm))
        }
    }
    val marks = remember(locale) {
        val format = NumberFormat.getNumberInstance(locale)
        RainPalette.LegendValues.map { format.format(it) to RainPalette.legendPosition(it).toFloat() }
    }
    ScaleLegend(colors, marks, stringResource(R.string.map_legend_unit), LegendHeight, modifier)
}

/**
 * Вертикальная полоса цвета [colors] (сверху вниз) высотой [height] с отметками [marks] (подпись и положение 0…1 снизу
 * вверх) и единицами [unit] под ней.
 */
@Composable
private fun ScaleLegend(colors: List<Color>, marks: List<Pair<String, Float>>, unit: String, height: Dp, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Column(
        modifier
            .glassButton(RoundedCornerShape(10.dp), glass)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        // Подписи стоят в своём столбце справа от полосы: его ширина по самой длинной подписи, рамка её вмещает.
        Row(Modifier.padding(vertical = 6.dp).height(height)) {
            Box(Modifier.size(width = 8.dp, height = height).background(Brush.verticalGradient(colors), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.height(height)) {
                for ((label, position) in marks) {
                    Text(
                        label,
                        style = pogodaText(10f, 500, color = palette.ink, tabular = true),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(y = height * (1f - position) - 7.dp),
                    )
                }
            }
        }
        Text(unit, style = pogodaText(9f, 500, color = palette.ink2))
    }
}

private val LegendHeight = 96.dp

/** Температурная шкала длиннее: на ней семь отметок через 10 °C. */
private val LegendTallHeight = 132.dp
private const val LEGEND_SAMPLES = 24

@Composable
private fun TimePanel(
    state: MapUiState,
    zone: ZoneId,
    status: String?,
    onDay: (MapDay) -> Unit,
    onPlay: () -> Unit,
    onTime: (Instant) -> Unit,
    onSettled: () -> Unit,
) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, HH:mm", locale) }
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(22.dp), glass)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sentenceCase(formatter.format(state.time.atZone(zone)), locale),
                style = pogodaText(20f, 700, color = palette.ink, tabular = true),
            )
            Spacer(Modifier.weight(1f))
            if (MapTimeline.isForecast(state.time, state.now)) {
                Text(stringResource(R.string.map_forecast), style = pogodaText(11f, 600, color = palette.accent))
            }
        }
        if (status != null) {
            Text(status, style = pogodaText(11f, 500, color = palette.ink2), modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((day, label) in listOf(
                MapDay.Today to R.string.map_day_now,
                MapDay.Tomorrow to R.string.map_day_tomorrow,
                MapDay.DayAfter to R.string.map_day_after,
            )) {
                DayChip(day, label, state.day, MapTimeline.hasFrames(day, state.now, zone), onDay)
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val playLabel = stringResource(if (state.playing) R.string.map_pause else R.string.map_play)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onPlay)
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).background(palette.button2, CircleShape), contentAlignment = Alignment.Center) {
                    LineIconView(if (state.playing) LineIcons.Pause else LineIcons.Play, 16.dp, palette.ink)
                }
            }
            TimeRuler(
                time = state.time,
                now = state.now,
                zone = zone,
                description = stringResource(R.string.map_time_scale),
                onTime = onTime,
                onSettled = onSettled,
                modifier = Modifier.weight(1f).height(44.dp),
            )
        }
        SourceLine(Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun DayChip(day: MapDay, label: Int, selected: MapDay, enabled: Boolean, onDay: (MapDay) -> Unit) {
    val palette = LocalPogodaPalette.current
    val shape = RoundedCornerShape(12.dp)
    val on = day == selected
    val color = when {
        !enabled -> palette.ink2.copy(alpha = 0.4f)
        on -> palette.ink
        else -> palette.ink2
    }
    Text(
        stringResource(label),
        style = pogodaText(12f, if (on) 600 else 500, color = color),
        modifier = Modifier
            .clip(shape)
            .then(if (on) Modifier.background(palette.buttonOn, shape) else Modifier)
            .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = { onDay(day) })
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * Источники у самих данных (условия DWD и BKG): «Quelle: Deutscher Wetterdienst» и «© GeoBasis-DE / BKG (год) CC BY 4.0»,
 * со ссылками на DWD, BKG и текст лицензии. Год это год получения данных (подложка грузится сейчас).
 */
@Composable
private fun SourceLine(modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val links = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val year = remember { LocalDate.now().year }
    val text = buildAnnotatedString {
        withLink(LinkAnnotation.Url(DWD_URL, links)) { append("Quelle: Deutscher Wetterdienst") }
        append(" · ")
        withLink(LinkAnnotation.Url(BKG_URL, links)) { append("© GeoBasis-DE / BKG $year") }
        append(" ")
        withLink(LinkAnnotation.Url(CC_BY_URL, links)) { append("CC BY 4.0") }
    }
    Text(text, style = pogodaText(9.5f, 500, color = palette.ink2), textAlign = TextAlign.Center, modifier = modifier.padding(vertical = 4.dp))
}

private const val DWD_URL = "https://www.dwd.de"
private const val BKG_URL = "https://www.bkg.bund.de"
private const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0/"
