package io.github.dumahuch.pogodaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dumahuch.pogodaapp.ui.PogodaTheme
import io.github.dumahuch.pogodaapp.ui.StartScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = AppVersion.read(this)
        setContent {
            PogodaTheme {
                StartScreen(version = version)
            }
        }
    }
}
