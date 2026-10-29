package io.github.dumahuch.pogodaapp.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette

/**
 * Прозрачность заглушек: мягко мигает, пока не пришли данные.
 * Если анимации выключены в системе (экономия энергии, «убрать анимацию»), заглушки неподвижны.
 */
@Composable
private fun skeletonAlpha(): Float {
    val animationsOn = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!animationsOn) return 1f
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
private fun Block(width: Dp, height: Dp, shape: Shape, alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .background(LocalPogodaPalette.current.ink.copy(alpha = 0.16f), shape),
    )
}

/** Заглушка вместо числа температуры и подписей. */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Column(modifier.padding(horizontal = 24.dp)) {
        Block(150.dp, 104.dp, RoundedCornerShape(20.dp), alpha, Modifier.padding(top = 6.dp))
        Block(210.dp, 20.dp, RoundedCornerShape(10.dp), alpha, Modifier.padding(top = 18.dp))
        Block(250.dp, 14.dp, RoundedCornerShape(10.dp), alpha, Modifier.padding(top = 10.dp))
    }
}

/** Заглушка вместо ленты по часам. */
@Composable
fun HourlySkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(6) {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Block(30.dp, 14.dp, RoundedCornerShape(10.dp), alpha)
                Block(26.dp, 26.dp, CircleShape, alpha)
                Block(30.dp, 14.dp, RoundedCornerShape(10.dp), alpha)
            }
        }
    }
}

/** Заглушка вместо строк списка по дням: название дня, значок и полоска температур. */
@Composable
fun DayRowsSkeleton(rows: Int, modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Block(54.dp, 14.dp, RoundedCornerShape(10.dp), alpha)
                Block(24.dp, 24.dp, CircleShape, alpha)
                Block(150.dp, 8.dp, RoundedCornerShape(10.dp), alpha)
            }
        }
    }
}

/** Заглушка вместо плиток вкладки «Подробно»: широкая плитка, две пары плиток и ещё одна широкая. */
@Composable
fun DetailsSkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    val shape = RoundedCornerShape(22.dp)
    val tint = LocalPogodaPalette.current.ink.copy(alpha = 0.16f)
    @Composable
    fun Tile(height: Dp, tileModifier: Modifier = Modifier) {
        Box(tileModifier.height(height).alpha(alpha).background(tint, shape))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(112.dp, Modifier.fillMaxWidth())
        repeat(2) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(132.dp, Modifier.weight(1f).fillMaxHeight())
                Tile(132.dp, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Tile(210.dp, Modifier.fillMaxWidth())
    }
}
