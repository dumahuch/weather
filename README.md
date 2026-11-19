**English** · [Русский](README.ru.md) · [Deutsch](README.de.md)

# PogodaApp

A weather app for Android, made by a non-developer with the help of AI.

I didn't like the existing weather apps for Germany, so I decided to make my own. The app is built for use in Germany and relies on open data sources.

My friends and I use it. It is a personal project, not a commercial product.

## Features

- Weather as an environment: a background of painted skies, with a calm, restrained interface of frosted glass on top.
- Four tabs: Home, Forecast, Details, Map.
- Official weather warnings (DWD) with the same levels and colors.
- DWD pollen index (8 species) and air quality on the European EAQI scale.
- DWD precipitation radar on the map, from −2 to +2 hours.
- Notifications about weather and warnings for the current location.
- Home screen widgets (2×2, 4×2, 4×4).
- Languages: Russian, German, English.
- Every effect can be turned off; Android's battery saver turns them off automatically.

## Data sources

| Data | Source | License / terms |
|---|---|---|
| Forecast, air quality | [Open-Meteo](https://open-meteo.com/) | [CC BY 4.0](https://open-meteo.com/en/licence), free for non-commercial use only |
| Pollen, radar | [Deutscher Wetterdienst (DWD)](https://www.dwd.de/) | Datenbasis: Deutscher Wetterdienst (GeoNutzV) |
| Warnings | DWD, delivered through [Bright Sky](https://brightsky.dev/) | DWD terms apply to the data; Bright Sky code is MIT |
| Place search | [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api), data from [GeoNames](https://www.geonames.org/) | CC BY 4.0 |

The app is not affiliated with or endorsed by DWD, Open-Meteo or GeoNames.

## Installation

Requires Android 16 or newer. The app has only been tested on the Pixel 6 Pro and Pixel 7; other phones are not guaranteed to work.

1. Open the [Releases](../../releases) page and download `PogodaApp-<version>.apk`.
2. Allow installing apps from this source when Android asks.
3. Install the file.

It is more convenient to install through [Obtainium](https://github.com/ImranR98/Obtainium): add this repository's address and updates will arrive on their own.

Every release is signed with the same key, so updates install over the existing version.

## Good to know

- A forecast is only a forecast. Don't base decisions that affect life or safety on it; rely on the official DWD warnings.
- Warnings and radar work only for Germany. Abroad, the app shows the forecast and air quality; the rest reads "no data" for now.
- The app is written by one person with the help of AI, so it has bugs. If something breaks, open an [issue](../../issues).

## Building from source

The project is written in Kotlin and built with Gradle. Developer notes (in Russian) are in [DEVELOPMENT.md](DEVELOPMENT.md).

```
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Application ID: `io.github.dumahuch.pogodaapp`.
