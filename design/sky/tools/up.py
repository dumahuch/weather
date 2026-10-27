import sys, time, torch, numpy as np
from PIL import Image
from spandrel import ModelLoader

torch.set_num_threads(2)
m = ModelLoader().load_from_file("RealESRGAN_x4plus_anime_6B.pth").model.eval()


def up4(img, tile=256, pad=16):
    """Увеличение в 4 раза по плиткам с перекрытием, чтобы хватило памяти."""
    a = torch.from_numpy(np.array(img.convert("RGB")).astype(np.float32) / 255).permute(2, 0, 1)[None]
    H, W = a.shape[2:]
    out = torch.zeros(1, 3, H * 4, W * 4)
    for y in range(0, H, tile):
        for x in range(0, W, tile):
            y0, x0 = max(y - pad, 0), max(x - pad, 0)
            y1, x1 = min(y + tile + pad, H), min(x + tile + pad, W)
            with torch.no_grad():
                o = m(a[:, :, y0:y1, x0:x1])
            cy, cx = (y - y0) * 4, (x - x0) * 4
            h, w = min(tile, H - y) * 4, min(tile, W - x) * 4
            out[:, :, y * 4:y * 4 + h, x * 4:x * 4 + w] = o[:, :, cy:cy + h, cx:cx + w]
    return Image.fromarray((out[0].permute(1, 2, 0).clamp(0, 1).numpy() * 255 + .5).astype(np.uint8))


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    box = tuple(map(int, sys.argv[3].split(","))) if len(sys.argv) > 3 else None
    im = Image.open(src).convert("RGB")
    if box:
        im = im.crop(box)
    t = time.time()
    r = up4(im)
    print("size", im.size, "->", r.size, "time %.1fs" % (time.time() - t))
    r.save(dst)
