"""Значки строки состояния с числом температуры: векторные ресурсы из цифр Manrope (жирный, wght 700)."""
import sys
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

root = Path(sys.argv[1])
font = instancer.instantiateVariableFont(TTFont(root / "app/src/main/res/font/manrope.ttf"), {"wght": 700})
cmap = font.getBestCmap()
gs = font.getGlyphSet()
LO, HI = -50, 55

def text_of(t):
    return ("−" if t < 0 else "") + str(abs(t)) + "°"

def outline(text):
    names = [cmap[ord(c)] for c in text]
    # Габариты всей строки в единицах шрифта.
    x = 0; boxes = []
    for n in names:
        bp = BoundsPen(gs); gs[n].draw(bp)
        if bp.bounds: boxes.append((bp.bounds[0] + x, bp.bounds[1], bp.bounds[2] + x, bp.bounds[3]))
        x += gs[n].width
    x0 = min(b[0] for b in boxes); x1 = max(b[2] for b in boxes)
    y0 = min(b[1] for b in boxes); y1 = max(b[3] for b in boxes)
    w, h = x1 - x0, y1 - y0
    k = min(23.0 / w, 19.0 / h)  # поля: ширина до 23, высота до 19 из 24
    ox = (24 - w * k) / 2 - x0 * k
    oy = (24 + h * k) / 2 + y0 * k  # переворот оси y
    pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    x = 0
    for n in names:
        gs[n].draw(TransformPen(pen, (k, 0, 0, -k, ox + x * k, oy)))
        x += gs[n].width
    return pen.getCommands()

out = root / "app/src/main/res/drawable"
for old in out.glob("ic_temp_*.xml"): old.unlink()
names = []
for t in range(LO, HI + 1):
    name = f"ic_temp_m{-t}" if t < 0 else f"ic_temp_{t}"
    names.append(name)
    (out / f"{name}.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Создано make_temp_icons.py: «{text_of(t)}» цифрами Manrope 700 для строки состояния. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{outline(text_of(t))}" />
</vector>
''', encoding="utf-8")

ids = "\n".join(f"        R.drawable.{n}," for n in names)
(root / "app/src/main/kotlin/io/github/dumahuch/pogodaapp/notify/TemperatureIcons.kt").write_text(f'''package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.R

// Создано .github/scripts/make_temp_icons.py, руками не править.

/**
 * Значки строки состояния «{text_of(LO)}» … «{text_of(HI)}»: векторные ресурсы, а не картинка на лету. Картинку в значке
 * строки состояния Android 16 не принял («Bad notification»), и приложение падало (0.7.0). За пределами берётся крайний.
 */
object TemperatureIcons {{
    private const val LOWEST = {LO}

    private val ids = intArrayOf(
{ids}
    )

    /** Значок для округлённой температуры. */
    fun of(rounded: Int): Int = ids[(rounded - LOWEST).coerceIn(0, ids.size - 1)]
}}
''', encoding="utf-8")
print(len(names))
