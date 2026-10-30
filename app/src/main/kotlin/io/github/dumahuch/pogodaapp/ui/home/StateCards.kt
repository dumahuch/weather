package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.pogodaText

/**
 * Карточка для состояний без данных: нет доступа к месту, место не определилось, нет сети и данных.
 * Основная кнопка действия и, если задана, вторая рядом: «Найти город» (места можно выбрать и без геолокации).
 */
@Composable
fun StateCard(
    icon: LineIcon,
    title: String,
    text: String,
    buttonLabel: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(shape, glass)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIconView(icon, 30.dp, palette.accent)
        Text(title, style = pogodaText(18f, 600, lineHeight = 1.3f))
        Text(text, style = pogodaText(14f, 500, color = palette.ink2, lineHeight = 1.45f))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(buttonLabel, onButton)
            if (secondaryLabel != null && onSecondary != null) ActionButton(secondaryLabel, onSecondary)
        }
    }
}

/** Кнопка карточки: рисуется 34 dp в высоту, а нажимается областью не меньше 48 dp. */
@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .height(34.dp)
                .background(LocalPogodaPalette.current.button2, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = pogodaText(13f, 600))
        }
    }
}

/** Строка «Нет сети · данные N ч назад» под сводкой погоды (оранжевая подсветка, как в макете). */
@Composable
fun OfflinePill(title: String, age: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val warning = LocalPogodaPalette.current.warning
    Row(
        modifier
            .background(warning.copy(alpha = 0.16f), shape)
            .border(1.dp, warning.copy(alpha = 0.30f), shape)
            .padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        LineIconView(LineIcons.CloudOff, 16.dp, warning)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                append(" · ")
                append(age)
            },
            style = pogodaText(12.5f, 500, lineHeight = 1.3f),
        )
    }
}
