package io.github.dumahuch.pogodaapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dumahuch.pogodaapp.data.ForecastCache
import io.github.dumahuch.pogodaapp.data.OpenMeteoClient
import io.github.dumahuch.pogodaapp.home.HomeViewModel
import io.github.dumahuch.pogodaapp.location.AndroidLocationSource
import io.github.dumahuch.pogodaapp.location.AndroidPlaceNamer
import io.github.dumahuch.pogodaapp.ui.PogodaTheme
import io.github.dumahuch.pogodaapp.ui.home.HomeActions
import io.github.dumahuch.pogodaapp.ui.home.HomeScreen
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val context = applicationContext
                HomeViewModel(
                    locations = AndroidLocationSource(context),
                    namer = AndroidPlaceNamer(context),
                    source = OpenMeteoClient(userAgent = "PogodaApp/${AppVersion.read(context).name}"),
                    cache = ForecastCache(File(context.filesDir, "forecast_cache.json")),
                )
            }
        }
    }

    /** Истина, если система больше не покажет окно запроса (отказ с «не спрашивать»). */
    private var permanentlyDenied by mutableStateOf(false)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(LOCATION_PERMISSION)
        viewModel.retry(permissionGranted = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Фон светлый, поэтому значки системных панелей тёмные.
        val transparentLight = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentLight, navigationBarStyle = transparentLight)

        val version = AppVersion.read(this)
        val versionLabel = getString(R.string.version_footer, version.name, version.code)
        val actions = HomeActions(
            onAllow = { permissionRequest.launch(LOCATION_PERMISSION) },
            onOpenSettings = ::openAppSettings,
            onRetry = { viewModel.retry(permissionGranted = hasLocationPermission()) },
        )
        setContent {
            PogodaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    permanentlyDenied = permanentlyDenied,
                    versionLabel = versionLabel,
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed(permissionGranted = hasLocationPermission())
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }

    private companion object {
        const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
