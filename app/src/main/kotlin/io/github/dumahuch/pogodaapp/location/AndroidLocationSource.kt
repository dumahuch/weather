package io.github.dumahuch.pogodaapp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Приблизительное место (разрешение ACCESS_COARSE_LOCATION): для погоды хватает нескольких километров.
 * Последнее известное место берётся сразу, свежее запрашивается у объединённого (FUSED) и сетевого поставщиков
 * одновременно, побеждает тот, кто ответил первым.
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    @SuppressLint("MissingPermission") // разрешение проверяется в usableManager() перед любым обращением
    override fun lastKnown(): GeoPoint? {
        val manager = usableManager() ?: return null
        val best = PROVIDERS
            .filter { it in manager.allProviders }
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    Log.w(TAG, "$provider: нет доступа к последнему месту", e)
                    null
                }
            }
            .maxByOrNull { it.time }
        Log.i(TAG, if (best == null) "последнего известного места нет" else "последнее известное место есть")
        return best?.toPoint()
    }

    override suspend fun fresh(): GeoPoint? {
        val manager = usableManager() ?: return null
        val providers = PROVIDERS.filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        Log.i(TAG, "доступные источники: $providers")
        if (providers.isEmpty()) return null
        val started = SystemClock.elapsedRealtime()
        return coroutineScope {
            val first = CompletableDeferred<Location?>()
            val requests = providers.map { provider ->
                launch {
                    val location = withTimeoutOrNull(FRESH_TIMEOUT_MS) { requestFresh(manager, provider) }
                    val took = SystemClock.elapsedRealtime() - started
                    Log.i(TAG, "$provider: ${if (location == null) "свежего места нет" else "место получено"} за $took мс")
                    if (location != null) first.complete(location)
                }
            }
            launch {
                requests.joinAll()
                first.complete(null)
            }
            val location = first.await()
            this.coroutineContext.cancelChildren()
            location?.toPoint()
        }
    }

    /** Служба местоположения, если доступ есть и геолокация включена; иначе null. */
    private fun usableManager(): LocationManager? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "нет разрешения на местоположение")
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null) {
            Log.w(TAG, "нет службы местоположения")
            return null
        }
        if (!manager.isLocationEnabled) {
            Log.i(TAG, "местоположение выключено в настройках телефона")
            return null
        }
        return manager
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFresh(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location: Location? ->
                continuation.resume(location)
            }
        }

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val TAG = "PogodaLocation"
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val FRESH_TIMEOUT_MS = 10_000L
    }
}
