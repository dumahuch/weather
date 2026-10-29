#!/usr/bin/env python3
"""Собирает пробный ответ Open-Meteo на 15 суток для тестов: app/src/test/resources/open-meteo/hannover-15d.json.

Зачем: сеть из среды разработки к Open-Meteo закрыта, а настоящий ответ на запрос версии 0.3.0 (суточные поля,
15 суток почасовых данных) нужен тестам разбора, вкладки «Прогноз» и проверке на эмуляторе. Структура, имена полей,
единицы и порядок значений повторяют ответ сервиса (https://open-meteo.com/en/docs), числа придуманы по
погоде Ганновера конца октября и не являются настоящим прогнозом. Файл детерминирован: повторный запуск даёт тот же результат.

Использование: make_fixture_15d.py <выходной файл>
"""
import json
import math
import sys

START = 1793142000  # 2026-10-28 00:00 по Берлину (UTC+1: летнее время закончилось 25 октября)
HOURS = 15 * 24

# сутки: код погоды (WMO), максимум, минимум, окна осадков (час начала, час конца, мм в час), вероятность осадков,
# ветер, порывы, УФ, восход и закат (час, минута); дальние сутки без вероятности, как отдаёт сервис
DAYS = [
    (1, 13.5, 8.3, [], 5, 14.2, 31.0, 1.5, (7, 11), (16, 49)),
    (61, 11.2, 6.1, [(2, 6, 0.4), (14, 18, 0.9)], 85, 24.1, 52.2, 0.9, (7, 13), (16, 47)),
    (3, 10.4, 5.8, [(9, 11, 0.2)], 40, 19.0, 38.5, 0.8, (7, 15), (16, 45)),
    (2, 9.9, 3.2, [], 10, 11.5, 26.3, 1.1, (7, 17), (16, 43)),
    (0, 8.7, 1.4, [], 5, 9.0, 20.1, 1.3, (7, 19), (16, 41)),
    (3, 7.6, 2.9, [(20, 23, 0.3)], 35, 16.4, 33.8, 0.7, (7, 21), (16, 39)),
    (71, 1.2, -2.4, [(5, 12, 0.6)], 70, 21.0, 44.0, 0.5, (7, 23), (16, 38)),
    (45, 2.0, -1.1, [], 15, 5.0, 12.0, 0.4, (7, 25), (16, 36)),
    (61, 6.4, 1.9, [(7, 20, 0.5)], 75, 26.7, 57.0, 0.6, (7, 27), (16, 35)),
    (80, 7.5, 3.3, [(10, 13, 1.8)], 65, 22.3, 48.6, 0.9, (7, 29), (16, 34)),
    (3, 6.8, 2.5, [], 30, 15.2, 30.1, 0.8, (7, 31), (16, 32)),
    (2, 8.1, 0.7, [], 20, 10.8, 22.4, 1.0, (7, 33), (16, 31)),
    (0, 9.0, 0.2, [], 10, 8.6, 18.0, 1.2, (7, 35), (16, 30)),
    (3, 7.2, 3.1, [(15, 18, 0.2)], None, 17.9, 36.4, 0.7, (7, 37), (16, 29)),
    (61, 6.0, 2.4, [(3, 9, 0.7)], None, 23.5, 49.9, 0.5, (7, 39), (16, 28)),
]


def hourly_temperature(day_index: int, hour: int) -> float:
    _, high, low, *_ = DAYS[day_index]
    # самое холодное около 6 часов утра, самое тёплое около 15 часов
    phase = math.cos((hour - 15) / 24 * 2 * math.pi)
    return round(low + (high - low) * (phase + 1) / 2, 1)


def main() -> None:
    times, temperature, codes, precipitation, is_day = [], [], [], [], []
    for index in range(HOURS):
        day, hour = divmod(index, 24)
        code, high, low, rain, _prob, _w, _g, _uv, rise, sset = DAYS[day]
        times.append(START + index * 3600)
        temperature.append(hourly_temperature(day, hour))
        mm = 0.0
        for begin, end, amount in rain:
            if begin <= hour < end:
                mm = amount
        # осадки записаны за час, который закончился в указанное время: здесь это значение часа до метки
        precipitation.append(mm)
        hour_code = code if (mm > 0 or code in (0, 1, 2, 3, 45)) else (3 if code in (61, 71, 80) else code)
        codes.append(hour_code)
        daylight = (rise[0] * 60 + rise[1]) <= hour * 60 + 30 < (sset[0] * 60 + sset[1])
        is_day.append(1 if daylight else 0)

    # сдвиг: запись N описывает осадки часа, закончившегося в её время, поэтому значения часа H лежат в записи H+1
    shifted = [0.0] + precipitation[:-1]

    daily = {"time": [], "weather_code": [], "temperature_2m_max": [], "temperature_2m_min": [],
             "precipitation_sum": [], "precipitation_probability_max": [], "wind_speed_10m_max": [],
             "wind_gusts_10m_max": [], "uv_index_max": [], "sunrise": [], "sunset": []}
    for day, (code, high, low, rain, prob, wind, gust, uv, rise, sset) in enumerate(DAYS):
        base = START + day * 86400
        daily["time"].append(base)
        daily["weather_code"].append(code)
        daily["temperature_2m_max"].append(high)
        daily["temperature_2m_min"].append(low)
        daily["precipitation_sum"].append(round(sum(a * (e - b) for b, e, a in rain), 1))
        daily["precipitation_probability_max"].append(prob)
        daily["wind_speed_10m_max"].append(wind)
        daily["wind_gusts_10m_max"].append(gust)
        daily["uv_index_max"].append(uv)
        daily["sunrise"].append(base + rise[0] * 3600 + rise[1] * 60)
        daily["sunset"].append(base + sset[0] * 3600 + sset[1] * 60)

    out = {
        "latitude": 52.365032, "longitude": 9.744498, "generationtime_ms": 0.5, "utc_offset_seconds": 3600,
        "timezone": "Europe/Berlin", "timezone_abbreviation": "GMT+1", "elevation": 65.0,
        "current_units": {"time": "unixtime", "interval": "seconds", "temperature_2m": "°C",
                          "apparent_temperature": "°C", "weather_code": "wmo code", "is_day": ""},
        "current": {"time": START + 12 * 3600 + 900, "interval": 900, "temperature_2m": 12.7,
                    "apparent_temperature": 10.8, "weather_code": 1, "is_day": 1},
        "hourly_units": {"time": "unixtime", "temperature_2m": "°C", "weather_code": "wmo code",
                         "precipitation": "mm", "is_day": ""},
        "hourly": {"time": times, "temperature_2m": temperature, "weather_code": codes,
                   "precipitation": shifted, "is_day": is_day},
        "daily_units": {"time": "unixtime", "weather_code": "wmo code", "temperature_2m_max": "°C",
                        "temperature_2m_min": "°C", "precipitation_sum": "mm",
                        "precipitation_probability_max": "%", "wind_speed_10m_max": "km/h",
                        "wind_gusts_10m_max": "km/h", "uv_index_max": "", "sunrise": "unixtime",
                        "sunset": "unixtime"},
        "daily": daily,
    }
    with open(sys.argv[1], "w", encoding="utf-8") as target:
        json.dump(out, target, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
