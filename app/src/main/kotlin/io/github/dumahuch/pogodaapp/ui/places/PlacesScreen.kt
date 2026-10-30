package io.github.dumahuch.pogodaapp.ui.places

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.places.PinResult
import io.github.dumahuch.pogodaapp.places.PinnedPlace
import io.github.dumahuch.pogodaapp.places.PlaceChoice
import io.github.dumahuch.pogodaapp.places.PlacesLogic
import io.github.dumahuch.pogodaapp.places.PlacesMessage
import io.github.dumahuch.pogodaapp.places.PlaceSuggestion
import io.github.dumahuch.pogodaapp.places.SearchResults
import io.github.dumahuch.pogodaapp.places.placeRegionLine
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassButton
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.BackButton
import io.github.dumahuch.pogodaapp.ui.pogodaText

/** Всё, что показывает экран мест. Собирается из состояния модели главного экрана. */
data class PlacesUiState(
    val selection: PlaceChoice,
    val pinned: List<PinnedPlace>,
    val message: PlacesMessage?,
    val results: SearchResults,
    val query: String,
)

/** Что делать по нажатиям на экране мест. Закрытием экрана и режимом поиска ведёт [io.github.dumahuch.pogodaapp.ui.PogodaApp]. */
class PlacesActions(
    val onSelect: (PlaceChoice) -> Unit,
    val onUnpin: (String) -> Unit,
    val onQuery: (String) -> Unit,
    /** Нажатие на найденный город: он открывается для просмотра и нигде не сохраняется. */
    val onPick: (PlaceSuggestion) -> Unit,
    /** Кнопка «Закрепить» у города, открытого для просмотра; null, если открыт не просмотр. */
    val onPinPreview: () -> PinResult?,
    /** Определить место по геолокации и закрепить; переданное действие вызывается, когда место закреплено. */
    val onLocate: (onDone: () -> Unit) -> Unit,
    /** Выход из поиска или с экрана: очищает поле и закрывает сообщение. */
    val onLeave: () -> Unit,
)

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Экран мест: список («Текущее место» и до пяти закреплённых) и, по кнопке «Найти город», поиск с подсказками.
 * [searching] и режим экрана хранит хозяин, чтобы кнопка «назад» вела из поиска в список, а из списка на «Главную».
 */
@Composable
fun PlacesScreen(
    ui: PlacesUiState,
    actions: PlacesActions,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searching) {
        SearchPage(ui, actions, onBack = { onSearching(false) }, onDone = onClose, modifier = modifier)
    } else {
        ListPage(ui, actions, onSearch = { onSearching(true) }, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun ListPage(
    ui: PlacesUiState,
    actions: PlacesActions,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(title = stringResource(R.string.places_title), onBack = onClose)
        Column(Modifier.fillMaxWidth().glassCard(CardShape, glass)) {
            PlaceRow(
                icon = LineIcons.Navigation,
                title = stringResource(R.string.place_current),
                subtitle = stringResource(R.string.places_current_sub),
                selected = ui.selection == PlaceChoice.Current,
                onClick = { actions.onSelect(PlaceChoice.Current); onClose() },
            )
            val preview = (ui.selection as? PlaceChoice.Preview)?.place
            if (preview != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
                PlaceRow(
                    icon = LineIcons.Search,
                    title = preview.name,
                    subtitle = listOf(
                        stringResource(R.string.places_preview),
                        placeRegionLine(preview.name, preview.admin1, preview.country),
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    selected = true,
                    onClick = onClose,
                    onPin = { actions.onPinPreview() },
                    pinEnabled = ui.pinned.size < PlacesLogic.MAX_PINNED,
                    pinLabel = stringResource(R.string.places_pin_named, preview.name),
                )
            }
            for (place in ui.pinned) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
                PlaceRow(
                    icon = LineIcons.Pin,
                    title = place.name,
                    subtitle = placeRegionLine(place.name, place.admin1, place.country).ifEmpty { null },
                    selected = ui.selection == PlaceChoice.Pinned(place.id),
                    onClick = { actions.onSelect(PlaceChoice.Pinned(place.id)); onClose() },
                    onRemove = { actions.onUnpin(place.id) },
                    removeLabel = stringResource(R.string.places_unpin, place.name),
                )
            }
        }
        val full = ui.pinned.size >= PlacesLogic.MAX_PINNED
        Text(
            if (full) {
                stringResource(R.string.places_full)
            } else {
                stringResource(R.string.places_count, ui.pinned.size, PlacesLogic.MAX_PINNED)
            },
            style = pogodaText(13f, 500, color = if (full) palette.warning else palette.ink2, lineHeight = 1.4f),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        WideButton(
            icon = LineIcons.Search,
            label = stringResource(R.string.places_find),
            onClick = onSearch,
        )
    }
}

@Composable
private fun SearchPage(
    ui: PlacesUiState,
    actions: PlacesActions,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            SearchField(initial = ui.query, onQuery = actions.onQuery, modifier = Modifier.weight(1f))
        }
        WideButton(
            icon = LineIcons.Locate,
            label = stringResource(R.string.places_locate),
            onClick = { if (ui.message != PlacesMessage.Locating) actions.onLocate(onDone) },
        )
        val message = ui.message
        if (message != null) {
            Text(
                stringResource(
                    when (message) {
                        PlacesMessage.ListFull -> R.string.places_full
                        PlacesMessage.Locating -> R.string.place_locating
                        PlacesMessage.LocateFailed -> R.string.places_locate_failed
                    },
                ),
                style = pogodaText(
                    13f,
                    500,
                    color = if (message == PlacesMessage.Locating) palette.ink2 else palette.warning,
                    lineHeight = 1.4f,
                ),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResultsCard(ui.results) { suggestion ->
                actions.onPick(suggestion)
                onDone()
            }
        }
        Text(
            stringResource(R.string.places_geonames),
            style = pogodaText(11.5f, 500, color = palette.ink2),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun ResultsCard(results: SearchResults, onPick: (PlaceSuggestion) -> Unit) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth().glassCard(CardShape, glass)) {
        when (results) {
            SearchResults.Idle -> InfoLine(stringResource(R.string.places_search_idle))
            SearchResults.Loading -> InfoLine(stringResource(R.string.places_search_loading))
            SearchResults.Empty -> InfoLine(stringResource(R.string.places_search_empty))
            SearchResults.Failed -> InfoLine(stringResource(R.string.places_search_failed))
            is SearchResults.Found -> results.items.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
                SuggestionRow(item, onClick = { onPick(item) })
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        style = pogodaText(14f, 500, color = LocalPogodaPalette.current.ink2, lineHeight = 1.45f),
        modifier = Modifier.padding(18.dp),
    )
}

/** Подсказка в три уровня: название, ниже область и страна (на случай одинаковых названий). */
@Composable
private fun SuggestionRow(item: PlaceSuggestion, onClick: () -> Unit) {
    val palette = LocalPogodaPalette.current
    val region = placeRegionLine(item.name, item.admin1, item.country)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(item.name, style = pogodaText(16f, 600, lineHeight = 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (region.isNotEmpty()) {
            Text(
                region,
                style = pogodaText(13f, 500, color = palette.ink2, lineHeight = 1.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text(title, style = pogodaText(20f, 600, lineHeight = 1.2f), maxLines = 1)
    }
}

/**
 * Строка списка мест. Нажатие по основной части выбирает место, отдельная кнопка с крестиком (область нажатия 48 dp)
 * открепляет закреплённое, а у города, открытого для просмотра, вместо неё кнопка с меткой закрепляет его ([onPin]).
 * У выбранного места стоит галочка.
 */
@Composable
private fun PlaceRow(
    icon: LineIcon,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "",
    onPin: (() -> Unit)? = null,
    pinEnabled: Boolean = true,
    pinLabel: String = "",
) {
    val palette = LocalPogodaPalette.current
    val selectedLabel = stringResource(R.string.places_selected)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { if (selected) stateDescription = selectedLabel }
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LineIconView(icon, 22.dp, if (selected) palette.accent else palette.ink2)
            Column(Modifier.weight(1f)) {
                Text(title, style = pogodaText(16f, 600, lineHeight = 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = pogodaText(13f, 500, color = palette.ink2, lineHeight = 1.35f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) LineIconView(LineIcons.Check, 20.dp, palette.accent)
        }
        if (onPin != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(enabled = pinEnabled, role = Role.Button, onClick = onPin)
                    .semantics { contentDescription = pinLabel },
                contentAlignment = Alignment.Center,
            ) {
                LineIconView(LineIcons.Pin, 20.dp, if (pinEnabled) palette.ink else palette.ink2.copy(alpha = 0.5f))
            }
        } else if (onRemove != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = removeLabel },
                contentAlignment = Alignment.Center,
            ) {
                LineIconView(LineIcons.Close, 18.dp, palette.ink2)
            }
        } else {
            Spacer(Modifier.size(10.dp))
        }
    }
}

/** Кнопка во всю ширину в стеклянном виде, высотой 52 dp. */
@Composable
private fun WideButton(icon: LineIcon, label: String, onClick: () -> Unit) {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glassButton(RoundedCornerShape(26.dp), LocalGlass.current)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        LineIconView(icon, 20.dp, palette.ink)
        Text(label, style = pogodaText(15f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Поле поиска. Текст держится здесь, а не берётся из потока модели: иначе при быстром наборе курсор прыгает.
 * Поле сразу в фокусе, клавиатура открыта.
 */
@Composable
private fun SearchField(initial: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val clearLabel = stringResource(R.string.places_clear)
    Row(
        modifier
            .height(44.dp)
            .glassButton(RoundedCornerShape(22.dp), LocalGlass.current)
            .padding(start = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIconView(LineIcons.Search, 18.dp, palette.ink2)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(
                    stringResource(R.string.places_search_hint),
                    style = pogodaText(15f, 500, color = palette.ink2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onQuery(it)
                },
                singleLine = true,
                textStyle = pogodaText(15f, 600, color = palette.ink),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clickable(enabled = text.isNotEmpty(), role = Role.Button) {
                    text = ""
                    onQuery("")
                }
                .semantics { contentDescription = clearLabel },
            contentAlignment = Alignment.Center,
        ) {
            if (text.isNotEmpty()) LineIconView(LineIcons.Close, 16.dp, palette.ink2)
        }
    }
}
