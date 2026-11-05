"""Фон «Ди» (слоистые бумажные горы): листы облаков и карты яркости для приложения.

Исходники:
  app/src/main/assets/sky/di/{scene}_{layer}.webp  слои сцен 1024×1536 (кладутся в приложение как есть)
  design/sky/di/clouds/cloud_light_NN.webp, cloud_storm_NN.webp  отдельные облака с прозрачностью

Что делает:
  1) Укладывает облака на два листа 2048×768 (светлые и грозовые) рядами без перекрытий, с зазором GAP,
     и пишет app/src/main/assets/sky/di/clouds_light.webp и clouds_storm.webp (WebP q90) и список
     прямоугольников облаков app/src/main/kotlin/.../sky/CloudSprites.kt: приложение рисует отдельные облака
     по любому числу и в любом месте, поэтому на листе они не должны задевать друг друга.
  2) Пишет app/src/main/assets/sky/di/maps.bin: уменьшенные копии всех слоёв (блоки 16×16) и листов облаков.
     По ним приложение считает цвет текста и плотность стекла, не читая кадр.

Формат maps.bin (big-endian, как у DataInputStream):
  4 байта 'SKYD', 1 байт версия (1), 1 байт число сцен (6), 1 байт число слоёв на сцену (6),
  2+2 байта ширина и высота карты слоя, 1 байт число листов облаков (2), 2+2 байта ширина и высота карты листа;
  затем карты слоёв: для каждой сцены из SCENES слои из LAYERS; затем листы из SHEETS.
  Каждая карта: ширина × высота × 4 байта R, G, B, A (цвет умножен на прозрачность).

Запуск из корня репозитория: python3 design/sky/di/tools/make_di.py (нужны pillow и numpy).
"""
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "sky" / "di"
CLOUDS = ROOT / "design" / "sky" / "di" / "clouds"

# Порядок как в приложении (enum SkyScene: Dawn, Morning, Afternoon, Sunset, Dusk, Night).
SCENES = ["dawn", "morning", "day", "sunset", "twilight", "night"]
LAYERS = ["sky", "far", "mid", "yellow", "coral", "front"]
SHEETS = ["light", "storm"]
BLOCK = 16
SHEET_W, SHEET_H = 2048, 768


def shrink(img: Image.Image) -> np.ndarray:
    a = np.asarray(img.convert("RGBA"), dtype=np.float64)
    h, w = a.shape[0], a.shape[1]
    if h % BLOCK or w % BLOCK:
        sys.exit(f"размер {w}×{h} не делится на {BLOCK}")
    alpha = a[..., 3:4] / 255.0
    premul = np.concatenate([a[..., :3] * alpha, a[..., 3:4]], axis=2)
    blocks = premul.reshape(h // BLOCK, BLOCK, w // BLOCK, BLOCK, 4).mean(axis=(1, 3))
    return np.clip(np.rint(blocks), 0, 255).astype(np.uint8)


GAP = 32
SPRITES_KT = ROOT / "app" / "src" / "main" / "kotlin" / "io" / "github" / "dumahuch" / "pogodaapp" / "sky" / "CloudSprites.kt"


def sheet(kind: str, count: int, scale: float) -> tuple[Image.Image, list[tuple[int, int, int, int]]]:
    """Облака рядами сверху вниз, самые высокие первыми; зазор [GAP] между соседями.

    Возвращает лист и прямоугольники (x, y, ширина, высота) в порядке имён файлов (cloud_{kind}_01, 02, …)."""
    files = sorted(CLOUDS.glob(f"cloud_{kind}_*.webp"))
    if len(files) != count:
        sys.exit(f"облаков {kind}: ожидалось {count}, найдено {len(files)}")
    clouds = []
    for f in files:
        c = Image.open(f).convert("RGBA")
        clouds.append(c.resize((round(c.width * scale), round(c.height * scale)), Image.LANCZOS))
    rects: list[tuple[int, int, int, int] | None] = [None] * count
    canvas = Image.new("RGBA", (SHEET_W, SHEET_H), (0, 0, 0, 0))
    x = y = GAP
    row_height = 0
    for i in sorted(range(count), key=lambda i: -clouds[i].height):
        c = clouds[i]
        if x + c.width + GAP > SHEET_W:
            x = GAP
            y += row_height + GAP
            row_height = 0
        if y + c.height + GAP > SHEET_H:
            sys.exit(f"облака {kind} не помещаются на лист {SHEET_W}×{SHEET_H}")
        canvas.alpha_composite(c, (x, y))
        rects[i] = (x, y, c.width, c.height)
        x += c.width + GAP
        row_height = max(row_height, c.height)
    return canvas, [r for r in rects if r is not None]


def write_sprites(rects: dict[str, list[tuple[int, int, int, int]]]) -> None:
    lines = [
        "package io.github.dumahuch.pogodaapp.sky",
        "",
        "// Создан design/sky/di/tools/make_di.py вместе с листами облаков: руками не править.",
        "",
        "/** Где на листах облаков фона «Ди» лежит каждое облако (пиксели листа 2048×768), порядок как у файлов в design/sky/di/clouds. */",
        "internal object CloudSprites {",
        "    class Rect(val x: Int, val y: Int, val w: Int, val h: Int)",
        "",
    ]
    for name, items in rects.items():
        lines.append(f"    val {name} = listOf(")
        for x, y, w, h in items:
            lines.append(f"        Rect({x}, {y}, {w}, {h}),")
        lines.append("    )")
        lines.append("")
    lines[-1] = "}"
    SPRITES_KT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{SPRITES_KT.relative_to(ROOT)}: {sum(len(v) for v in rects.values())} облаков")


def main() -> None:
    packed = {
        "light": sheet("light", 8, 0.9),
        "storm": sheet("storm", 6, 0.95),
    }
    sheets = {name: img for name, (img, _) in packed.items()}
    write_sprites({name: rects for name, (_, rects) in packed.items()})
    for name, img in sheets.items():
        out = ASSETS / f"clouds_{name}.webp"
        img.save(out, "WEBP", quality=90, method=6, exact=False)
        print(f"{out.relative_to(ROOT)}: {out.stat().st_size} байт")

    layer_maps = []
    lw = lh = None
    for scene in SCENES:
        for layer in LAYERS:
            m = shrink(Image.open(ASSETS / f"{scene}_{layer}.webp"))
            if lw is None:
                lh, lw = m.shape[0], m.shape[1]
            elif (m.shape[1], m.shape[0]) != (lw, lh):
                sys.exit(f"{scene}_{layer}: другой размер")
            layer_maps.append(m)
    sheet_maps = [shrink(Image.open(ASSETS / f"clouds_{name}.webp")) for name in SHEETS]
    sh, sw = sheet_maps[0].shape[0], sheet_maps[0].shape[1]

    out = ASSETS / "maps.bin"
    with out.open("wb") as f:
        f.write(b"SKYD")
        f.write(struct.pack(">BBBHH", 1, len(SCENES), len(LAYERS), lw, lh))
        f.write(struct.pack(">BHH", len(SHEETS), sw, sh))
        for m in layer_maps + sheet_maps:
            f.write(m.tobytes())
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size} байт, слоёв {len(layer_maps)} ({lw}×{lh}), листов {len(sheet_maps)} ({sw}×{sh})")


if __name__ == "__main__":
    main()
