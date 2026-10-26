"""Outline the Jarvis lockup from the project's Inter font; no font rendering at runtime."""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.boundsPen import BoundsPen
import sys

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('app/src/main/res/font/inter.ttf')
font = instantiateVariableFont(TTFont(source), {'wght': 750}, inplace=False)
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
scale = 35 / font['OS/2'].sCapHeight
navy, orange = '#07182F', '#F4511E'
paths = []
x = 1.0
for char in 'Jarvis':
    glyph = glyphs[cmap[ord(char)]]
    rec = DecomposingRecordingPen(glyphs); glyph.draw(rec)
    contours, current = [], []
    for op, pts in rec.value:
        if op == 'moveTo' and current: contours.append(current); current = []
        current.append((op, pts))
        if op in ('closePath', 'endPath'): contours.append(current); current = []
    if current: contours.append(current)
    # Counters must share their glyph's path. Separate filled contours close the hole of "a".
    body = SVGPathPen(glyphs)
    dot = SVGPathPen(glyphs)
    has_dot = False
    for contour in contours:
        bounds = BoundsPen(glyphs)
        for op, pts in contour: getattr(bounds, op)(*pts)
        is_dot = char == 'i' and bounds.bounds and bounds.bounds[1] > font['OS/2'].sxHeight
        target = dot if is_dot else body
        has_dot |= bool(is_dot)
        transform = TransformPen(target, (scale, 0, 0, -scale, x, 37))
        for op, pts in contour: getattr(transform, op)(*pts)
    paths.append((navy, body.getCommands(), "evenOdd" if char == "a" else "nonZero"))
    if has_dot: paths.append((orange, dot.getCommands(), "nonZero"))
    x += glyph.width * scale - 0.75
width = x + 1
small_scale = 10.0 / font['OS/2'].sCapHeight
tracking = 13.0
small_width = sum(glyphs[cmap[ord(c)]].width * small_scale for c in 'FILES') + tracking * 4
x = (width - small_width) / 2
for char in 'FILES':
    glyph = glyphs[cmap[ord(char)]]
    pen = SVGPathPen(glyphs)
    glyph.draw(TransformPen(pen, (small_scale, 0, 0, -small_scale, x, 51)))
    paths.append((orange, pen.getCommands(), "nonZero"))
    x += glyph.width * small_scale + tracking
xml = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- Jarvis / FILES: Inter outlines, orange i dot, scalable brand lockup. -->', f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width:.2f}dp" android:height="54dp" android:viewportWidth="{width:.2f}" android:viewportHeight="54">']
for color, path, fill_rule in paths: xml.append(f'    <path android:fillColor="{color}" android:fillType="{fill_rule}" android:pathData="{path}" />')
xml.append('</vector>')
Path('app/src/main/res/drawable/jarvis_wordmark_brand.xml').write_text('\n'.join(xml)+'\n')
print(f'Wordmark: {width:.2f} x 54, {len(paths)} contours')
