package io.github.dumahuch.pogodaapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.settings.CardShadow
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.BackButton
import io.github.dumahuch.pogodaapp.ui.pogodaText

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Экран настроек (с версии 0.5.4): пока один пункт, «Тень карточек», две ступени. Карточка с выбором сама стеклянная,
 * поэтому смена ступени видна на ней сразу. Открывается шестерёнкой на «Главной», закрывается кнопкой «назад».
 */
@Composable
fun SettingsScreen(
    cardShadow: CardShadow,
    onCardShadow: (CardShadow) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose)
            Text(stringResource(R.string.settings_title), style = pogodaText(20f, 600, lineHeight = 1.2f), maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().glassCard(CardShape, glass).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_card_shadow),
                style = pogodaText(15f, 600),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
            )
            Column(Modifier.selectableGroup()) {
                ShadowOption(
                    title = stringResource(R.string.settings_shadow_soft),
                    hint = stringResource(R.string.settings_shadow_soft_hint),
                    selected = cardShadow == CardShadow.Soft,
                    onClick = { onCardShadow(CardShadow.Soft) },
                )
                ShadowOption(
                    title = stringResource(R.string.settings_shadow_strong),
                    hint = stringResource(R.string.settings_shadow_strong_hint),
                    selected = cardShadow == CardShadow.Strong,
                    onClick = { onCardShadow(CardShadow.Strong) },
                )
            }
        }
    }
}

/** Одна ступень: название, пояснение и галочка у выбранной. Нажатие по всей строке (высота не меньше 60 dp). */
@Composable
private fun ShadowOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPogodaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = pogodaText(16f, if (selected) 600 else 500))
            Text(hint, style = pogodaText(13f, 400, color = palette.ink2))
        }
        if (selected) {
            LineIconView(LineIcons.Check, 22.dp, palette.accent)
        } else {
            Spacer(Modifier.size(22.dp))
        }
    }
}
