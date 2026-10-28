package io.github.dumahuch.pogodaapp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Приблизительное место (разрешение ACCESS_COARSE_LOCATION): для погоды хватает нескольких километров.
 * Сначала просим свежее место у объединённого поставщика Android (FUSED), затем у сетевого;
 * если свежего нет, берём последнее известное.
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    @SuppressLint("MissingPermission") // разрешение проверяется ниже, перед любым обращением
    override suspend fun currentLocation(): GeoPoint? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!manager.isLocationEnabled) return null
        val providers = PROVIDERS.filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        for (provider in providers) {
            val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MS) { requestFresh(manager, provider) }
            if (fresh != null) return fresh.toPoint()
        }
        for (provider in providers) {
            val known = manager.getLastKnownLocation(provider)
            if (known != null) return known.toPoint()
        }
        return null
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
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val FRESH_TIMEOUT_MS = 10_000L
    }
}
