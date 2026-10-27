"""Картины для макета из увеличенных файлов design/sky/2x. Запуск из этой папки: python3 make_assets.py 1920 1280 82 76
(ширина, высота, качество неба, качество облаков). Результат: assets/<время>_p.webp и <время>_c.webp."""
import sys, io, base64, json
from PIL import Image
W,H=int(sys.argv[1]),int(sys.argv[2]); qp,qc=int(sys.argv[3]),int(sys.argv[4])
T=["dawn","morning","afternoon","sunset","dusk","night"]
tot=0
for t in T:
    pl=Image.open(f"../sky/2x/sky_{t}_plate.webp").convert("RGB").resize((W,H),Image.LANCZOS)
    cl=Image.open(f"../sky/2x/sky_{t}_clouds.webp").convert("RGBA").resize((W,H),Image.LANCZOS)
    pl.save(f"assets/{t}_p.webp","WEBP",quality=qp,method=6)
    cl.save(f"assets/{t}_c.webp","WEBP",quality=qc,alpha_quality=90,method=6)
import os
s=sum(os.path.getsize(f"assets/{f}") for f in os.listdir("assets"))
print(W,H,"total KB",s//1024)
for f in sorted(os.listdir("assets")): print(f, os.path.getsize(f"assets/{f}")//1024)
