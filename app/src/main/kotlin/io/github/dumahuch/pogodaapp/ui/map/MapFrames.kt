package io.github.dumahuch.pogodaapp.ui.map

import io.github.dumahuch.pogodaapp.data.Bzip2
import io.github.dumahuch.pogodaapp.data.HttpJson
import io.github.dumahuch.pogodaapp.map.FieldLoader
import io.github.dumahuch.pogodaapp.map.FolderListings
import io.github.dumahuch.pogodaapp.map.ModelFrameDisk
import io.github.dumahuch.pogodaapp.map.ModelFrames
import io.github.dumahuch.pogodaapp.map.ModelRainLoader
import java.io.File

/**
 * Кадры модели ICON-D2 из файлов DWD (opendata.dwd.de) с диском по прогонам в [root]; один набор загрузчиков и один
 * список папок на всех, кто спрашивает. Строят карта на экране и утренняя подгрузка.
 */
fun modelFrames(userAgent: String, root: File, log: (String) -> Unit = {}): ModelFrames {
    val listings = FolderListings { url -> HttpJson.get(url, userAgent, SOURCE) }
    val bytes = { url: String -> HttpJson.bytes(url, userAgent, SOURCE, "application/octet-stream") }
    return ModelFrames(
        fields = FieldLoader(listings, bytes, Bzip2::unpack),
        rain = ModelRainLoader(listings, bytes, Bzip2::unpack),
        disk = ModelFrameDisk(root),
        log = log,
    )
}

private const val SOURCE = "DWD"
