package io.github.dumahuch.pogodaapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dumahuch.pogodaapp.data.DwdPollenClient
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoGeocodingClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoAirQualityClient
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.home.HomeViewModel
import io.github.dumahuch.pogodaapp.location.Locations
import io.github.dumahuch.pogodaapp.location.AndroidPlaceNamer
import io.github.dumahuch.pogodaapp.location.AssetPollenRegionLocator
import io.github.dumahuch.pogodaapp.places.FilePinnedPlacesStorage
import io.github.dumahuch.pogodaapp.places.PinResult
import io.github.dumahuch.pogodaapp.places.PinnedForecastCaches
import io.github.dumahuch.pogodaapp.ui.PogodaApp
import io.github.dumahuch.pogodaapp.ui.PogodaTheme
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.places.PlacesActions
import io.github.dumahuch.pogodaapp.ui.places.PlacesUiState
import io.github.dumahuch.pogodaapp.ui.sky.SkyCheckModel
import io.github.dumahuch.pogodaapp.ui.sky.SkyCheckPanel
import io.github.dumahuch.pogodaapp.ui.sky.SkyDiagnostics
import io.github.dumahuch.pogodaapp.ui.sky.SkyHost
import io.github.dumahuch.pogodaapp.ui.sky.TapSequence
import io.github.dumahuch.pogodaapp.ui.sky.skyInput
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val context = applicationContext
                HomeViewModel(
                    locations = Locations.create(context),
                    namer = AndroidPlaceNamer(context),
                    source = OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"),
                    cache = ForecastCache(File(context.filesDir, "forecast_cache.json")),
                    air = OpenMeteoAirQualityClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"),
                    pollenSource = DwdPollenClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"),
                    pollenRegions = AssetPollenRegionLocator(context),
                    pinnedStorage = FilePinnedPlacesStorage(File(context.filesDir, "pinned_places.json")),
                    pinnedCaches = PinnedForecastCaches(context.filesDir),
                    geocoding = OpenMeteoGeocodingClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"),
                    language = { searchLanguage(context) },
                )
            }
        }
    }

    /** Экран проверки неба: открыт ли и что подставлено вместо настоящих времени и погоды. */
    private val skyCheck: SkyCheckModel by viewModels()

    /** Истина, если система больше не покажет окно запроса (отказ с «не спрашивать»). */
    private var permanentlyDenied by mutableStateOf(false)

    /** Что сделать, когда место закреплено после запроса доступа из экрана мест (кнопка «Определить место»). */
    private var afterLocatePermission: (() -> Unit)? = null

    private val locatePermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Отказ тоже идёт дальше: определение места тогда не удастся и экран покажет сообщение об этом.
        val done = afterLocatePermission
        afterLocatePermission = null
        if (done != null) pinCurrentLocation(done)
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(LOCATION_PERMISSION)
        viewModel.retry(permissionGranted = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Начальный вид панелей: прозрачные, значки тёмные (фон светлый). Дальше цвет значков следует за небом.
        val transparentLight = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentLight, navigationBarStyle = transparentLight)

        val version = AppVersion.read(this)
        val versionLabel = getString(R.string.version_footer, version.name, version.code)
        val actions = HomeActions(
            onAllow = { permissionRequest.launch(LOCATION_PERMISSION) },
            onOpenSettings = ::openAppSettings,
            onRetry = { viewModel.retry(permissionGranted = hasLocationPermission()) },
            onRefresh = {
                // Строка в журнале нужна проверке на эмуляторе: по ней видно, что протягивание сработало.
                Log.i(REFRESH_LOG_TAG, "обновление протягиванием")
                viewModel.refresh(permissionGranted = hasLocationPermission())
            },
        )
        val placesActions = PlacesActions(
            onSelect = { choice -> viewModel.select(choice, permissionGranted = hasLocationPermission()) },
            onUnpin = { id -> viewModel.unpin(id, permissionGranted = hasLocationPermission()) },
            onQuery = viewModel.search::onQuery,
            onPick = viewModel::pin,
            onLocate = { done ->
                if (hasLocationPermission()) {
                    pinCurrentLocation(done)
                } else {
                    afterLocatePermission = done
                    locatePermissionRequest.launch(LOCATION_PERMISSION)
                }
            },
            onLeave = {
                viewModel.search.clear()
                viewModel.dismissMessage()
            },
        )
        setContent {
            PogodaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val selection by viewModel.selection.collectAsStateWithLifecycle()
                val pinned by viewModel.pinned.collectAsStateWithLifecycle()
                val message by viewModel.message.collectAsStateWithLifecycle()
                val query by viewModel.search.query.collectAsStateWithLifecycle()
                val results by viewModel.search.results.collectAsStateWithLifecycle()
                val places = PlacesUiState(selection, pinned, message, results, query)
                val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
                val checkOpen by skyCheck.open.collectAsStateWithLifecycle()
                val override by skyCheck.override.collectAsStateWithLifecycle()
                val glassBlur by skyCheck.glassBlur.collectAsStateWithLifecycle()
                val diagnostics = remember { SkyDiagnostics() }
                val taps = remember { TapSequence() }
                val input = remember(state) { state.skyInput() }
                SkyHost(
                    input = input,
                    override = override,
                    glassBlur = glassBlur,
                    diagnostics = if (checkOpen) diagnostics else null,
                    onDarkIcons = ::setDarkBarIcons,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        PogodaApp(
                            state = state,
                            refreshing = refreshing,
                            permanentlyDenied = permanentlyDenied,
                            versionLabel = versionLabel,
                            actions = actions,
                            places = places,
                            placesActions = placesActions,
                            onVersionTap = { if (taps.tap(SystemClock.uptimeMillis())) skyCheck.show() },
                        )
                        if (checkOpen) {
                            SkyCheckPanel(
                                input = input,
                                override = override,
                                diagnostics = diagnostics,
                                glassBlur = glassBlur,
                                onOverride = skyCheck::set,
                                onGlassBlur = skyCheck::setGlassBlur,
                                onClose = skyCheck::close,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed(permissionGranted = hasLocationPermission())
    }

    private fun pinCurrentLocation(done: () -> Unit) {
        viewModel.pinCurrentLocation { result -> if (result != PinResult.ListFull) done() }
    }

    /** Значки системных панелей: тёмные на светлом небе, светлые на тёмном. */
    private fun setDarkBarIcons(dark: Boolean) {
        val controller = window.insetsController ?: return
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(if (dark) mask else 0, mask)
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }

    private companion object {
        const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
        const val REFRESH_LOG_TAG = "PogodaRefresh"
        val SEARCH_LANGUAGES = setOf("ru", "de", "en")

        /** Язык подсказок поиска: язык интерфейса, если приложение на нём есть, иначе английский. */
        fun searchLanguage(context: Context): String {
            val language = context.resources.configuration.locales[0].language
            return if (language in SEARCH_LANGUAGES) language else "en"
        }
    }
}
