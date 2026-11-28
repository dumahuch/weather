package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.data.ForecastSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Один запрос прогноза на один запуск фоновой задачи. Виджет, уведомление о погоде и вечерняя сводка берут ответ отсюда:
 * первый, кому он нужен, делает запрос, остальные получают тот же ответ (или ту же ошибку, без повторных попыток в
 * том же запуске). Для другого места запрос делается заново. Экземпляр живёт один запуск задачи.
 */
class JobForecast(private val source: ForecastSource) {
    private val lock = Mutex()
    private var key: Pair<Double, Double>? = null
    private var result: Result<String>? = null

    /** Сырой ответ прогноза для места; при сбое бросает ту же ошибку, что и источник. */
    suspend fun raw(latitude: Double, longitude: Double): String = lock.withLock {
        val wanted = latitude to longitude
        val known = result
        if (known != null && key == wanted) return@withLock known.getOrThrow()
        val fresh = try {
            Result.success(source.fetchRaw(latitude, longitude))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        key = wanted
        result = fresh
        fresh.getOrThrow()
    }
}
