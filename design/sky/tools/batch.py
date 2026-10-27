"""Увеличение картин неба в 2 раза.
Панорамы и облачные слои: ИИ-увеличение (Real-ESRGAN anime) в 4 раза, затем уменьшение до 2 раз
и смешивание 65 % ИИ + 35 % обычное увеличение (чтобы остались мягкость и мазки).
Облака: цвет и прозрачность увеличиваются отдельно; цвет под прозрачными местами сначала
заполняется ближайшим цветом облака, чтобы не появились тёмные края.
Небо без облаков: обычное увеличение (плавные градиенты, ИИ тут не нужен)."""
import sys, time, numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi
from up import up4

SRC = "../sky/"
OUT = "out/"
TIMES = ["morning", "dawn", "afternoon", "sunset", "dusk", "night"]
W2, H2 = 3072, 2048


def classic(img):
    return img.resize((W2, H2), Image.LANCZOS).filter(ImageFilter.UnsharpMask(2, 50, 2))


def ai_rgb(img):
    ai = up4(img).resize((W2, H2), Image.LANCZOS)
    return Image.blend(classic(img), ai, .65)


def fill_transparent(rgba):
    a = np.array(rgba).astype(np.uint8)
    solid = a[..., 3] > 8
    idx = ndi.distance_transform_edt(~solid, return_distances=False, return_indices=True)
    rgb = a[..., :3][tuple(idx)]
    return Image.fromarray(rgb), Image.fromarray(a[..., 3])


for t in TIMES:
    t0 = time.time()
    pano = Image.open(f"{SRC}sky_{t}.png").convert("RGB")
    ai_rgb(pano).save(f"{OUT}sky_{t}_2x.png")
    print(t, "panorama done %.0fs" % (time.time() - t0), flush=True)

    plate = Image.open(f"{SRC}sky_{t}_plate.png").convert("RGB")
    plate.resize((W2, H2), Image.LANCZOS).save(f"{OUT}sky_{t}_plate_2x.png")

    cl = Image.open(f"{SRC}sky_{t}_clouds.png").convert("RGBA")
    rgb, alpha = fill_transparent(cl)
    rgb2 = ai_rgb(rgb)
    alpha2 = alpha.resize((W2, H2), Image.LANCZOS)
    out = rgb2.copy()
    out.putalpha(alpha2)
    out.save(f"{OUT}sky_{t}_clouds_2x.png")
    print(t, "clouds done, total %.0fs" % (time.time() - t0), flush=True)
print("ALL DONE", flush=True)
