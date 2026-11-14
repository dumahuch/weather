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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import java.util.Locale

/**
 * Подложка basemap.de (BKG, тёмный стиль) и наши слои: облака под дождём, заливка температуры, числа температуры и
 * стрелки ветра, все под подписями городов, сверху метка места. Какие слои видны, решает [layer]. Из подписей подложки
 * остаются только города и общины ([MapLabels]). Карта живёт, пока экран на месте: её жизненный цикл идёт за экраном
 * приложения, при уходе с вкладки она закрывается.
 */
@Composable
fun MapLibreHost(
    images: MapImages,
    layer: MapLayer,
    place: Place?,
    locateRequests: Int,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failed by rememberUpdatedState(onFailed)
    val placeNow by rememberUpdatedState(place)
    // Карта открывается на месте человека; если место стало известно уже после открытия, камера переходит один раз.
    var centered by remember { mutableStateOf(false) }
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
            val start = placeNow?.let { LatLng(it.latitude, it.longitude) }
            ready.cameraPosition = CameraPosition.Builder().target(start ?: LatLng(START_LAT, START_LON)).zoom(START_ZOOM).build()
            if (start != null) centered = true
            ready.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addOverlays(loaded)
                style = loaded
            }
            map = ready
        }
    }

    LaunchedEffect(style, images.rain) {
        style?.getSourceAs<ImageSource>(RAIN_SOURCE)?.setImage(images.rain ?: emptyBitmap())
    }
    LaunchedEffect(style, images.clouds) {
        style?.getSourceAs<ImageSource>(CLOUD_SOURCE)?.setImage(images.clouds ?: emptyBitmap())
    }
    LaunchedEffect(style, images.temperature) {
        style?.getSourceAs<ImageSource>(TEMP_SOURCE)?.setImage(images.temperature ?: emptyBitmap())
    }
    LaunchedEffect(style, images.numbers) {
        style?.getSourceAs<GeoJsonSource>(NUMBERS_SOURCE)?.setGeoJson(images.numbers ?: EMPTY_JSON)
    }
    LaunchedEffect(style, images.arrows) {
        style?.getSourceAs<GeoJsonSource>(ARROWS_SOURCE)?.setGeoJson(images.arrows ?: EMPTY_JSON)
    }
    LaunchedEffect(style, layer) {
        val ready = style ?: return@LaunchedEffect
        show(ready, CLOUD_LAYER, layer == MapLayer.CloudsAndRain)
        show(ready, RAIN_LAYER, layer == MapLayer.CloudsAndRain || layer == MapLayer.RainOnly)
        show(ready, TEMP_LAYER, layer == MapLayer.Temperature)
        show(ready, NUMBERS_LAYER, layer == MapLayer.Temperature)
        show(ready, ARROWS_LAYER, layer == MapLayer.Wind)
    }
    LaunchedEffect(style, place) {
        style?.getSourceAs<GeoJsonSource>(PLACE_SOURCE)?.setGeoJson(placeJson(place))
    }
    LaunchedEffect(map, place) {
        val ready = map ?: return@LaunchedEffect
        if (centered || place == null) return@LaunchedEffect
        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), START_ZOOM))
        centered = true
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
private const val TEMP_SOURCE = "pogoda-temp"
private const val TEMP_LAYER = "pogoda-temp-layer"
private const val NUMBERS_SOURCE = "pogoda-numbers"
private const val NUMBERS_LAYER = "pogoda-numbers-layer"
private const val ARROWS_SOURCE = "pogoda-arrows"
private const val ARROWS_LAYER = "pogoda-arrows-layer"
private const val PLACE_SOURCE = "pogoda-place"
private const val EMPTY_JSON = """{"type":"FeatureCollection","features":[]}"""
private const val MIN_ZOOM = 4.6
private const val MAX_ZOOM = 10.0
private const val START_LAT = 51.2
private const val START_LON = 10.4
private const val START_ZOOM = 7.0
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

/** Показывает или прячет слой [id]. */
private fun show(style: Style, id: String, visible: Boolean) {
    style.getLayer(id)?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
}

/**
 * Слои облаков, дождя, температуры и ветра под первым слоем подписей стиля (подписи городов остаются читаемыми и
 * занимают места первыми), метка места сверху. Слои температуры и ветра сначала скрыты: их показывает выбор слоя.
 */
private fun addOverlays(style: Style) {
    hideExtraLabels(style)
    val box = MapArea.Overlay
    val quad = LatLngQuad(
        LatLng(box.north, box.west),
        LatLng(box.north, box.east),
        LatLng(box.south, box.east),
        LatLng(box.south, box.west),
    )
    for ((name, icon) in MapIcons.all()) style.addImage(name, icon)
    style.addSource(ImageSource(CLOUD_SOURCE, quad, emptyBitmap()))
    style.addSource(ImageSource(RAIN_SOURCE, quad, emptyBitmap()))
    style.addSource(ImageSource(TEMP_SOURCE, quad, emptyBitmap()))
    style.addSource(GeoJsonSource(NUMBERS_SOURCE, EMPTY_JSON))
    style.addSource(GeoJsonSource(ARROWS_SOURCE, EMPTY_JSON))
    val cloudLayer = RasterLayer(CLOUD_LAYER, CLOUD_SOURCE).withProperties(
        PropertyFactory.rasterFadeDuration(0f),
        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
    )
    val rainLayer = RasterLayer(RAIN_LAYER, RAIN_SOURCE).withProperties(
        PropertyFactory.rasterFadeDuration(0f),
        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
    )
    val tempLayer = RasterLayer(TEMP_LAYER, TEMP_SOURCE).withProperties(
        PropertyFactory.rasterFadeDuration(0f),
        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
        PropertyFactory.visibility(Property.NONE),
    )
    val numbersLayer = SymbolLayer(NUMBERS_LAYER, NUMBERS_SOURCE).withProperties(
        PropertyFactory.iconImage(Expression.get("i")),
        PropertyFactory.iconAllowOverlap(false),
        PropertyFactory.iconIgnorePlacement(false),
        PropertyFactory.iconPadding(4f),
        PropertyFactory.visibility(Property.NONE),
    )
    val arrowsLayer = SymbolLayer(ARROWS_LAYER, ARROWS_SOURCE).withProperties(
        PropertyFactory.iconImage(Expression.get("i")),
        PropertyFactory.iconRotate(Expression.get("d")),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PropertyFactory.iconAllowOverlap(false),
        PropertyFactory.iconIgnorePlacement(false),
        PropertyFactory.iconPadding(6f),
        PropertyFactory.visibility(Property.NONE),
    )
    val labels = style.layers.firstOrNull { it is SymbolLayer }?.id
    for (layer in listOf(cloudLayer, rainLayer, tempLayer, numbersLayer, arrowsLayer)) {
        if (labels != null) style.addLayerBelow(layer, labels) else style.addLayer(layer)
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
