#!/usr/bin/env python3
"""Делает файл сохранённого прогноза (формат ForecastCache) для проверки без сети.

Берёт настоящий ответ Open-Meteo из тестов, сдвигает всё время на целое число часов,
чтобы первый час ленты совпал с текущим часом, и записывает как прогноз, сохранённый N часов назад.

Использование: make_cache.py <ответ.json> <выходной файл> [--age-hours 3] [--now <unix>]
"""
import argparse
import json
import time


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("fixture")
    parser.add_argument("output")
    parser.add_argument("--age-hours", type=int, default=3)
    parser.add_argument("--now", type=int, default=None)
    args = parser.parse_args()

    now = args.now if args.now is not None else int(time.time())
    with open(args.fixture, encoding="utf-8") as source:
        data = json.load(source)

    shift = ((now - data["hourly"]["time"][0]) // 3600) * 3600
    data["hourly"]["time"] = [t + shift for t in data["hourly"]["time"]]
    data["current"]["time"] += shift
    data["daily"]["time"] = [t + shift for t in data["daily"]["time"]]
    # Восход и закат (ответ версии 0.3.0 и новее) — секунды Unix, сдвигаются вместе с остальным временем.
    for key in ("sunrise", "sunset"):
        if key in data["daily"]:
            data["daily"][key] = [t + shift if isinstance(t, int) else t for t in data["daily"][key]]

    entry = {
        "v": 1,
        "savedAtMs": (now - args.age_hours * 3600) * 1000,
        "lat": 52.37,
        "lon": 9.74,
        "name": "Hannover",
        "raw": json.dumps(data, ensure_ascii=False, separators=(",", ":")),
    }
    with open(args.output, "w", encoding="utf-8") as target:
        json.dump(entry, target, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
