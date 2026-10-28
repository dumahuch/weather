package io.github.dumahuch.pogodaapp.sky

import io.github.dumahuch.pogodaapp.weather.Condition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Общие помощники тестов неба: настоящие карты яркости и значения, снятые с макета. */
internal object SkyTestData {

    /** Файл карт яркости из приложения (тесты запускаются в папке модуля app). */
    val maps: SkyMaps by lazy {
        File("src/main/assets/${SkyMaps.ASSET_PATH}").inputStream().use { SkyMaps.read(it) }
    }

    /** Значения, снятые с макета в браузере (версия 21): для сверки расчётов приложения с тем, что одобрил Дима. */
    class Golden(private val json: JSONObject) {
        val hour: Double = json.getDouble("h")
        val elevation: Double = json.getDouble("e")
        val dph: Double = json.getDouble("dph")
        val cloudPosition: Double = json.getDouble("uc")
        val condition: Condition = conditionOf(json.getString("wk"))
        val weather: SkyWeather = SkyWeather.of(condition)
        val label: String get() = "h=$hour ${json.getString("wk")} dph=$dph"

        fun weight(scene: SkyScene): Double = json.getJSONObject("sw").getDouble(scene.fileName)

        fun rgb(key: String): Rgb = rgbOf(json.getJSONArray(key))

        fun veil(): Veil {
            val an = json.getJSONObject("an")
            return Veil(rgbOf(an.getJSONArray("top")), rgbOf(an.getJSONArray("mid")), rgbOf(an.getJSONArray("hor")))
        }

        /** Фон под частями экрана, как его снял макет. */
        fun backdrops(): Backdrops {
            val bk = json.getJSONObject("bk")
            fun region(name: String) = RegionColors(
                mean = rgbOf(bk.getJSONArray(name)),
                underDarkInk = rgbOf(bk.getJSONArray(name + "D")),
                underLightInk = rgbOf(bk.getJSONArray(name + "L")),
            )
            return Backdrops(rgb("top"), rgb("mid"), rgb("hor"), region("hero"), region("alert"), region("card"), region("dock"))
        }

        fun side(name: String): JSONObject = json.getJSONObject(name)

        fun theme(name: String): JSONObject = json.getJSONObject(name)
    }

    fun golden(): List<Golden> {
        val text = SkyTestData::class.java.getResourceAsStream("/sky/golden.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val array = JSONArray(text)
        return (0 until array.length()).map { Golden(array.getJSONObject(it)) }
    }

    fun conditionOf(key: String): Condition = when (key) {
        "clear" -> Condition.Clear
        "partly" -> Condition.Partly
        "cloudy" -> Condition.Cloudy
        "rain" -> Condition.Rain
        "storm" -> Condition.Storm
        "snow" -> Condition.Snow
        "fog" -> Condition.Fog
        else -> error("Неизвестный вид погоды в данных макета: $key")
    }

    fun rgbOf(array: JSONArray) = Rgb(array.getDouble(0), array.getDouble(1), array.getDouble(2))

    fun weightsOf(g: Golden): SceneWeights = SkyTimeOfDay.weights(g.elevation, g.hour)

    fun look(g: Golden): SkyLook = SkyLook(weightsOf(g), g.weather, g.veil(), g.cloudPosition)

    fun distance(a: Rgb, b: Rgb): Double = maxOf(kotlin.math.abs(a.r - b.r), kotlin.math.abs(a.g - b.g), kotlin.math.abs(a.b - b.b))
}
