package io.github.dumahuch.pogodaapp.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Название места от системной службы адресов Android (на Pixel это служба Google).
 * Без сети или службы возвращает null, тогда на экране «Текущее место».
 */
class AndroidPlaceNamer(private val context: Context) : PlaceNamer {

    override suspend fun nameOf(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, context.resources.configuration.locales[0])
        val addresses = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<List<Address>> { continuation ->
                geocoder.getFromLocation(
                    point.latitude,
                    point.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume(emptyList())
                        }
                    },
                )
            }
        }
        val address = addresses?.firstOrNull() ?: return null
        return address.locality ?: address.subAdminArea ?: address.adminArea
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
