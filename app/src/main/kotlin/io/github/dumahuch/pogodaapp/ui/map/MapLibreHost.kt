package io.github.dumahuch.pogodaapp.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dumahuch.pogodaapp.map.MapArea
import io.github.dumahuch.pogodaapp.map.MapLabels
import io.github.dumahuch.pogodaapp.weather.Place
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import java.util.Locale

/**
 * Подложка basemap.de (BKG, тёмный стиль) и наши слои: облака под дождём, оба под подписями городов, сверху метка места.
 * Из подписей подложки остаются только города и общины ([MapLabels]). Карта живёт, пока экран на месте: её жизненный цикл идёт за экраном приложения, при уходе с вкладки она закрывается.
 */
@Composable
fun MapLibreHost(
    rain: Bitmap?,
    clouds: Bitmap?,
    cloudsVisible: Boolean,
    place: Place?,
    locateRequests: Int,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failed by rememberUpdatedState(onFailed)
    val mapView = remember {
        MapLibre.getInstance(context)
        keepNothingOnDisk(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .logoEnabled(false)
            // Подпись источников (DWD, BKG) показывает сам экран карты, под картой.
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) mapView.onStart().also { started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) mapView.onResume().also { resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) mapView.onPause().also { resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) mapView.onStop().also { started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { failed() }
        mapView.getMapAsync { ready ->
            ready.setMinZoomPreference(MIN_ZOOM)
            ready.setMaxZoomPreference(MAX_ZOOM)
            val target = MapArea.CameraTarget
            ready.setLatLngBoundsForCameraTarget(LatLngBounds.from(target.north, target.east, target.south, target.west))
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(START_LAT, START_LON)).zoom(START_ZOOM).build()
            ready.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addOverlays(loaded)
                style = loaded
            }
            map = ready
        }
    }

    LaunchedEffect(style, rain) {
        style?.getSourceAs<ImageSource>(RAIN_SOURCE)?.setImage(rain ?: emptyBitmap())
    }
    LaunchedEffect(style, clouds) {
        style?.getSourceAs<ImageSource>(CLOUD_SOURCE)?.setImage(clouds ?: emptyBitmap())
    }
    LaunchedEffect(style, cloudsVisible) {
        style?.getLayer(CLOUD_LAYER)?.setProperties(
            PropertyFactory.visibility(if (cloudsVisible) Property.VISIBLE else Property.NONE),
        )
    }
    LaunchedEffect(style, place) {
        style?.getSourceAs<GeoJsonSource>(PLACE_SOURCE)?.setGeoJson(placeJson(place))
    }
    LaunchedEffect(map, locateRequests) {
        val ready = map ?: return@LaunchedEffect
        if (locateRequests == 0 || place == null) return@LaunchedEffect
        ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), LOCATE_ZOOM), LOCATE_MS)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val STYLE_URL = "https://basemap.de/data/produkte/web_vektor/styles/bm_web_drk.json"
private const val RAIN_SOURCE = "pogoda-rain"
private const val RAIN_LAYER = "pogoda-rain-layer"
private const val CLOUD_SOURCE = "pogoda-clouds"
private const val CLOUD_LAYER = "pogoda-clouds-layer"
private const val PLACE_SOURCE = "pogoda-place"
private const val MIN_ZOOM = 4.6
private const val MAX_ZOOM = 10.0
private const val START_LAT = 51.2
private const val START_LON = 10.4
private const val START_ZOOM = 5.0
private const val LOCATE_ZOOM = 7.5
private const val LOCATE_MS = 600

/**
 * Библиотека карты по умолчанию складывает скачанные плитки подложки на диск телефона (до 50 МБ). Здесь это выключено,
 * а накопленное стирается: подложка при каждом открытии грузится заново. Вызывать до создания карты.
 */
private fun keepNothingOnDisk(context: Context) {
    val offline = OfflineManager.getInstance(context.applicationContext)
    offline.setMaximumAmbientCacheSize(0L, null)
    offline.clearAmbientCache(null)
}

/** Скрывает подписи подложки, кроме городов и общин; слои остаются в стиле (наши слои встают под первым из них). */
private fun hideExtraLabels(style: Style) {
    val ids = style.layers.filterIsInstance<SymbolLayer>().map { it.id }
    for (id in MapLabels.hidden(ids)) {
        style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.NONE))
    }
}

private fun emptyBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

/** Слои облаков и дождя под первым слоем подписей стиля (подписи городов остаются читаемыми), метка места сверху. */
private fun addOverlays(style: Style) {
    hideExtraLabels(style)
    val box = MapArea.Overlay
    val quad = LatLngQuad(
        LatLng(box.north, box.west),
        LatLng(box.north, box.east),
        LatLng(box.south, box.east),
        LatLng(box.south, box.west),
    )
    style.addSource(ImageSource(CLOUD_SOURCE, quad, emptyBitmap()))
    style.addSource(ImageSource(RAIN_SOURCE, quad, emptyBitmap()))
    val cloudLayer = RasterLayer(CLOUD_LAYER, CLOUD_SOURCE).withProperties(
        PropertyFactory.rasterFadeDuration(0f),
        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
    )
    val rainLayer = RasterLayer(RAIN_LAYER, RAIN_SOURCE).withProperties(
        PropertyFactory.rasterFadeDuration(0f),
        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
    )
    val labels = style.layers.firstOrNull { it is SymbolLayer }?.id
    if (labels != null) {
        style.addLayerBelow(cloudLayer, labels)
        style.addLayerBelow(rainLayer, labels)
    } else {
        style.addLayer(cloudLayer)
        style.addLayer(rainLayer)
    }
    style.addSource(GeoJsonSource(PLACE_SOURCE, placeJson(null)))
    style.addLayer(
        CircleLayer("pogoda-place-ring", PLACE_SOURCE).withProperties(
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleOpacity(0f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeOpacity(0.85f),
        ),
    )
    style.addLayer(
        CircleLayer("pogoda-place-dot", PLACE_SOURCE).withProperties(
            PropertyFactory.circleRadius(4.5f),
            PropertyFactory.circleColor("#FFFFFF"),
        ),
    )
}

/** Метка места для источника GeoJSON: точка или пустой набор. */
private fun placeJson(place: Place?): String {
    if (place == null) return """{"type":"FeatureCollection","features":[]}"""
    val point = String.format(Locale.ROOT, "[%.5f,%.5f]", place.longitude, place.latitude)
    return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":$point}}]}"""
}
