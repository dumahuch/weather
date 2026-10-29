#!/usr/bin/env python3
"""Превращает границы районов пыльцы DWD (GeoJSON, градусы WGS84) в компактный файл для приложения.

Вход: GeoJSON FeatureCollection, у каждого объекта в свойствах номер района. Имя свойства ищется среди
partregion_id, PARTREGION_ID, region_id, REGION_ID и т. п.; у районов без частей берётся номер земли.
Выход: app/src/main/assets/pollen/regions.json в формате, который читает PollenRegions.parse:
  {"regions":[{"id":11,"polygons":[[[lon,lat,lon,lat,...],[дыра...]],...]}]}
Кольца упрощены (Дуглас-Пекер, допуск 0,01° около 1 км) и округлены до 4 знаков (около 10 м).

Использование: make_regions.py вход.geojson выход.json [--expect 27]
"""
import json
import math
import sys

TOLERANCE_DEG = 0.01
DECIMALS = 4

ID_KEYS = ("partregion_id", "PARTREGION_ID", "partregionid", "PartregionID")
PARENT_KEYS = ("region_id", "REGION_ID", "regionid", "RegionID")


def region_key(props):
    """Номер района: номер части, а если части нет (или он -1), номер земли."""
    for key in ID_KEYS:
        if key in props and props[key] not in (None, "", "-1", -1):
            return int(props[key])
    for key in PARENT_KEYS:
        if key in props and props[key] not in (None, ""):
            return int(props[key])
    raise ValueError(f"в свойствах нет номера района: {sorted(props)}")


def perpendicular(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(p[0] - ax, p[1] - ay)
    t = max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(p[0] - (ax + t * dx), p[1] - (ay + t * dy))


def simplify(points, tol):
    """Дуглас-Пекер без рекурсии."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        lo, hi = stack.pop()
        far, index = 0.0, -1
        for i in range(lo + 1, hi):
            d = perpendicular(points[i], points[lo], points[hi])
            if d > far:
                far, index = d, i
        if far > tol and index >= 0:
            keep[index] = True
            stack.append((lo, index))
            stack.append((index, hi))
    return [p for p, k in zip(points, keep) if k]


def ring_flat(ring):
    pts = [(float(x), float(y)) for x, y, *_ in ring]
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts = pts[:-1]
    # Кольцо замкнутое: упрощаем с разрезом в самой дальней точке, чтобы не потерять угол в начале.
    if len(pts) > 3:
        far = max(range(len(pts)), key=lambda i: math.hypot(pts[i][0] - pts[0][0], pts[i][1] - pts[0][1]))
        a = simplify(pts[: far + 1], TOLERANCE_DEG)
        b = simplify(pts[far:] + [pts[0]], TOLERANCE_DEG)
        pts = a[:-1] + b[:-1]
    if len(pts) < 3:
        return None
    flat = []
    for x, y in pts:
        flat += [round(x, DECIMALS), round(y, DECIMALS)]
    return flat


def polygons_of(geometry):
    kind = geometry["type"]
    if kind == "Polygon":
        yield geometry["coordinates"]
    elif kind == "MultiPolygon":
        yield from geometry["coordinates"]
    else:
        raise ValueError(f"неожиданный тип геометрии: {kind}")


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    expect = int(argv[argv.index("--expect") + 1]) if "--expect" in argv else None
    data = json.load(open(argv[1], encoding="utf-8"))
    regions = {}
    for feature in data["features"]:
        key = region_key(feature.get("properties") or {})
        for polygon in polygons_of(feature["geometry"]):
            rings = [r for r in (ring_flat(ring) for ring in polygon) if r]
            if rings:
                regions.setdefault(key, []).append(rings)
    if expect is not None and len(regions) != expect:
        print(f"районов {len(regions)}, ожидалось {expect}: {sorted(regions)}", file=sys.stderr)
        return 1
    out = {"regions": [{"id": k, "polygons": regions[k]} for k in sorted(regions)]}
    text = json.dumps(out, ensure_ascii=False, separators=(",", ":"))
    with open(argv[2], "w", encoding="utf-8") as f:
        f.write(text)
    print(f"районов {len(regions)}, файл {len(text) / 1024:.0f} КБ")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
