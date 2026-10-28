"""Карты яркости для приложения: из каждой картины неба получается уменьшенная копия 192×128.

Берутся 12 картин из design/sky/2x (небо без облаков `_plate` и облака с прозрачностью `_clouds`).
Каждая делится на блоки 16×16 точек, берётся среднее. У облаков среднее считается с учётом
прозрачности (цвет умножен на прозрачность, как хранит холст в браузере и как нужно для наложения).
Результат: app/src/main/assets/sky/maps.bin. Приложение читает его и считает по нему цвет текста
и плотность стекла, не читая кадр.

Формат (порядок байт big-endian, как у DataInputStream):
  4 байта  'SKYM'
  1 байт   версия формата (1)
  1 байт   число карт (12)
  2 байта  ширина, 2 байта высота (у всех одинаковые)
  далее карты в порядке: для каждого времени суток из SCENES сначала небо без облаков, затем облака;
  каждая карта: ширина × высота × 4 байта R, G, B, A (цвет уже умножен на прозрачность).

Запуск: python3 design/sky/tools/make_maps.py (из корня репозитория). Нужны pillow и numpy.
"""
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "design" / "sky" / "2x"
OUT = ROOT / "app" / "src" / "main" / "assets" / "sky" / "maps.bin"

# Порядок как в приложении (enum SkyScene).
SCENES = ["dawn", "morning", "afternoon", "sunset", "dusk", "night"]
KINDS = ["plate", "clouds"]
W, H = 192, 128


def shrink(path: Path) -> np.ndarray:
    img = Image.open(path).convert("RGBA")
    a = np.asarray(img, dtype=np.float64)
    if a.shape[0] % H or a.shape[1] % W:
        sys.exit(f"{path.name}: размер {a.shape[1]}×{a.shape[0]} не делится на {W}×{H}")
    ky, kx = a.shape[0] // H, a.shape[1] // W
    alpha = a[..., 3:4] / 255.0
    premul = np.concatenate([a[..., :3] * alpha, a[..., 3:4]], axis=2)
    blocks = premul.reshape(H, ky, W, kx, 4).mean(axis=(1, 3))
    return np.clip(np.rint(blocks), 0, 255).astype(np.uint8)


def main() -> None:
    maps = []
    for scene in SCENES:
        for kind in KINDS:
            maps.append(shrink(SRC / f"sky_{scene}_{kind}.webp"))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("wb") as f:
        f.write(b"SKYM")
        f.write(struct.pack(">BBHH", 1, len(maps), W, H))
        for m in maps:
            f.write(m.tobytes())
    print(f"{OUT.relative_to(ROOT)}: {OUT.stat().st_size} байт, карт {len(maps)}")


if __name__ == "__main__":
    main()
