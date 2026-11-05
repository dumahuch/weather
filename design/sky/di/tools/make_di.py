"""Фон «Ди» (слоистые бумажные горы): листы облаков и карты яркости для приложения.

Исходники:
  app/src/main/assets/sky/di/{scene}_{layer}.webp  слои сцен 1024×1536 (кладутся в приложение как есть)
  design/sky/di/clouds/cloud_light_NN.webp, cloud_storm_NN.webp  отдельные облака с прозрачностью

Что делает:
  1) Раскладывает облака на два листа 2048×768 (светлые и грозовые) по постоянной раскладке
     и пишет app/src/main/assets/sky/di/clouds_light.webp и clouds_storm.webp (WebP q90).
  2) Пишет app/src/main/assets/sky/di/maps.bin: уменьшенные копии всех слоёв (блоки 16×16) и листов облаков.
     По ним приложение считает цвет текста и плотность стекла, не читая кадр.

Формат maps.bin (big-endian, как у DataInputStream):
  4 байта 'SKYD', 1 байт версия (1), 1 байт число сцен (6), 1 байт число слоёв на сцену (6),
  2+2 байта ширина и высота карты слоя, 1 байт число листов облаков (2), 2+2 байта ширина и высота карты листа;
  затем карты слоёв: для каждой сцены из SCENES слои из LAYERS; затем листы из SHEETS.
  Каждая карта: ширина × высота × 4 байта R, G, B, A (цвет умножен на прозрачность).

Запуск из корня репозитория: python3 design/sky/di/tools/make_di.py (нужны pillow и numpy).
"""
import random
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


def sheet(kind: str, count: int, scale: float, rows: list[float], seed: int) -> Image.Image:
    """Облака по кругу листа слева направо, по рядам [rows] (доля высоты листа), с постоянным случайным разбросом."""
    rng = random.Random(seed)
    canvas = Image.new("RGBA", (SHEET_W, SHEET_H), (0, 0, 0, 0))
    files = sorted(CLOUDS.glob(f"cloud_{kind}_*.webp"))
    if len(files) != count:
        sys.exit(f"облаков {kind}: ожидалось {count}, найдено {len(files)}")
    order = files[:]
    rng.shuffle(order)
    step = SHEET_W / len(order)
    for i, f in enumerate(order):
        cloud = Image.open(f).convert("RGBA")
        k = scale * rng.uniform(0.85, 1.1)
        cloud = cloud.resize((round(cloud.width * k), round(cloud.height * k)), Image.LANCZOS)
        cx = step * (i + 0.5) + rng.uniform(-0.2, 0.2) * step
        cy = rows[i % len(rows)] * SHEET_H + rng.uniform(-0.04, 0.04) * SHEET_H
        x = round(cx - cloud.width / 2)
        y = round(min(max(cy - cloud.height / 2, 0), SHEET_H - cloud.height))
        x = min(max(x, 0), SHEET_W - cloud.width)
        canvas.alpha_composite(cloud, (x, y))
    return canvas


def main() -> None:
    sheets = {
        "light": sheet("light", 8, 0.62, [0.18, 0.55, 0.32, 0.72], seed=7),
        "storm": sheet("storm", 6, 0.95, [0.25, 0.6, 0.4], seed=11),
    }
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
