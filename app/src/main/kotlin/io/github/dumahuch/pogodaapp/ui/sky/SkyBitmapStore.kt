package io.github.dumahuch.pogodaapp.ui.sky

import android.content.Context
import android.graphics.ImageDecoder
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.dumahuch.pogodaapp.sky.SkyScene
import io.github.dumahuch.pogodaapp.sky.SkyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Картина и облака одного времени суток. */
internal class SceneBitmaps(val plate: ImageBitmap, val clouds: ImageBitmap)

/**
 * Держит в памяти картины неба: не больше [SkyState.MAX_SCENES] времён суток (по две картины на каждое).
 * Картины лежат в памяти видеокарты (аппаратные изображения): процессор их не читает, а рисование быстрое.
 * Перед загрузкой лишние выгружаются, так что памяти занято не больше, чем нужно двум временам суток.
 * Состояние читается при рисовании, поэтому кадр обновляется, как только картина загрузилась.
 */
@Stable
internal class SkyBitmapStore(private val context: Context) {

    private val loaded = mutableStateMapOf<SkyScene, SceneBitmaps>()

    fun get(scene: SkyScene): SceneBitmaps? = loaded[scene]

    /** Выгружает все картины (например, когда приложение ушло с экрана). */
    fun clear() {
        loaded.clear()
    }

    /**
     * Оставляет в памяти ровно [scenes] и догружает недостающие. Пустой набор ничего не меняет:
     * пока места нет, картины остаются, чтобы небо не мигало.
     */
    suspend fun ensure(scenes: Set<SkyScene>) {
        if (scenes.isEmpty()) return
        require(scenes.size <= SkyState.MAX_SCENES) { "Нельзя держать больше ${SkyState.MAX_SCENES} времён суток" }
        for (scene in loaded.keys.toList()) {
            if (scene !in scenes) loaded.remove(scene)
        }
        for (scene in scenes) {
            if (loaded.containsKey(scene)) continue
            val bitmaps = withContext(Dispatchers.IO) { decode(scene) } ?: continue
            loaded[scene] = bitmaps
        }
    }

    private fun decode(scene: SkyScene): SceneBitmaps? = try {
        SceneBitmaps(read("${scene.fileName}_plate"), read("${scene.fileName}_clouds"))
    } catch (e: IOException) {
        Log.w(TAG, "Не удалось прочитать картину неба ${scene.fileName}", e)
        null
    }

    private fun read(name: String): ImageBitmap {
        val source = ImageDecoder.createSource(context.assets, "sky/$name.webp")
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val TAG = "PogodaSky"
    }
}
