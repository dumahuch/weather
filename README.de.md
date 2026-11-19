[English](README.md) · [Русский](README.ru.md) · **Deutsch**

# PogodaApp

Eine Wetter-App für Android, entstanden mit Hilfe von KI und ohne Entwicklerhintergrund.

Mir haben die vorhandenen Wetter-Apps für Deutschland nicht gefallen, also habe ich beschlossen, etwas Eigenes zu bauen. Die App ist für den Einsatz in Deutschland gedacht und stützt sich auf offene Datenquellen.

Ich und meine Freunde nutzen sie. Es ist ein privates Projekt, kein kommerzielles Produkt.

## Funktionen

- Wetter als Umgebung: ein Hintergrund aus gemalten Himmelsbildern, darüber eine ruhige, schlichte Oberfläche mit Milchglas-Optik.
- Vier Tabs: Start, Vorhersage, Details, Karte.
- Amtliche Wetterwarnungen (DWD) mit denselben Stufen und Farben.
- DWD-Pollenflugindex (8 Arten) und Luftqualität nach dem europäischen EAQI.
- DWD-Niederschlagsradar auf der Karte, von −2 bis +2 Stunden.
- Benachrichtigungen zu Wetter und Warnungen für den aktuellen Ort.
- Widgets für den Startbildschirm (2×2, 4×2, 4×4).
- Sprachen: Russisch, Deutsch, Englisch.
- Jeder Effekt lässt sich abschalten; der Energiesparmodus von Android schaltet sie von selbst aus.

## Datenquellen

| Daten | Quelle | Lizenz / Bedingungen |
|---|---|---|
| Vorhersage, Luftqualität | [Open-Meteo](https://open-meteo.com/) | [CC BY 4.0](https://open-meteo.com/en/licence), nur für nicht-kommerzielle Nutzung kostenlos |
| Pollen, Radar | [Deutscher Wetterdienst (DWD)](https://www.dwd.de/) | Datenbasis: Deutscher Wetterdienst (GeoNutzV) |
| Warnungen | DWD, geliefert über [Bright Sky](https://brightsky.dev/) | Für die Daten gelten die DWD-Bedingungen; Bright-Sky-Code unter MIT |
| Ortssuche | [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api), Daten von [GeoNames](https://www.geonames.org/) | CC BY 4.0 |

Die App steht in keiner Verbindung zu DWD, Open-Meteo und GeoNames und wird von ihnen nicht unterstützt.

## Installation

Benötigt Android 16 oder neuer. Die App wurde nur auf dem Pixel 6 Pro und dem Pixel 7 getestet; auf anderen Geräten ist die Funktion nicht garantiert.

1. Öffne die Seite [Releases](../../releases) und lade die Datei `PogodaApp-<Version>.apk` herunter.
2. Erlaube die Installation von Apps aus dieser Quelle, wenn Android danach fragt.
3. Installiere die Datei.

Bequemer geht es über [Obtainium](https://github.com/ImranR98/Obtainium): Füge die Adresse dieses Repositorys hinzu, dann kommen die Updates von allein.

Jede Version ist mit demselben Schlüssel signiert, deshalb lassen sich Updates über die installierte Version einspielen.

## Gut zu wissen

- Eine Vorhersage bleibt eine Vorhersage. Triff auf dieser Grundlage keine Entscheidungen, von denen Leben oder Sicherheit abhängen; verlasse dich auf die amtlichen DWD-Warnungen.
- Warnungen und Radar funktionieren nur für Deutschland. Im Ausland zeigt die App Vorhersage und Luftqualität, der Rest steht vorerst auf „keine Daten“.
- Die App wird von einer einzelnen Person mit Hilfe von KI geschrieben, daher enthält sie Fehler. Wenn etwas nicht funktioniert, eröffne ein [Issue](../../issues).

## Aus dem Quellcode bauen

Das Projekt ist in Kotlin geschrieben und wird mit Gradle gebaut. Entwicklernotizen (auf Russisch): [DEVELOPMENT.md](DEVELOPMENT.md).

```
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Anwendungs-ID: `io.github.dumahuch.pogodaapp`.
