package io.github.dumahuch.pogodaapp.ui.sky

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Можно ли двигать небо. Нельзя, если в системе выключены анимации («убрать анимацию» или нулевой масштаб
 * в настройках разработчика) или включена экономия энергии: тогда облака стоят на месте.
 */
internal fun animationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    return ValueAnimator.areAnimatorsEnabled() && scale != 0f && !powerSave
}

/** Следит за [animationsEnabled]: пересчитывает, когда меняется экономия энергии или масштаб анимаций. */
@Composable
internal fun rememberAnimationsEnabled(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(animationsEnabled(context)) }
    DisposableEffect(context) {
        val update = { state.value = animationsEnabled(context) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) = update()
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = update()
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // Могло измениться, пока слежения не было.
        update()
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return state
}
