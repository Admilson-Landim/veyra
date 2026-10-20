#!/usr/bin/env python3
"""Renders Veyra's legacy launcher icons, the Play Store icon and the feature graphic.

The mark is the same as app/src/main/res/drawable/ic_launcher_foreground.xml (a "V" whose right
stroke ends in a note head, Champagne on Charcoal). Needs rsvg-convert and ImageMagick (magick).
Run from the repository root:  python3 store/render_graphics.py
"""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
OUT = ROOT / "store/graphics"
CHARCOAL, CHAMPAGNE, IVORY = "#17181C", "#D8C8A4", "#F3F0E9"

# The mark in the adaptive icon's 108x108 viewport.
MARK = f"""
  <path d="M37,38L54,72L68,44" fill="none" stroke="{CHAMPAGNE}" stroke-width="4.5"
        stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="72" cy="40" r="5" fill="{CHAMPAGNE}"/>"""


def render(svg: str, size: tuple[int, int], target: pathlib.Path) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    png = target.with_suffix(".png")
    subprocess.run(["rsvg-convert", "-w", str(size[0]), "-h", str(size[1]), f.name, "-o", str(png)], check=True)
    if target.suffix == ".webp":
        # Lossless WebP, the format the project's mipmaps already use (Android 4.2+).
        subprocess.run(["magick", str(png), "-define", "webp:lossless=true", str(target)], check=True)
        png.unlink()


def legacy_icon(round_shape: bool) -> str:
    # Launchers before Android 8 show the file as is: draw the shape ourselves, inset like the
    # 2 dp margin of a 48 dp legacy icon. The view box keeps the adaptive icon's visible area.
    shape = ('<circle cx="54" cy="54" r="36" fill="%s"/>' % CHARCOAL if round_shape
             else '<rect x="18" y="18" width="72" height="72" rx="14" fill="%s"/>' % CHARCOAL)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="16 16 76 76">{shape}{MARK}</svg>'


def store_icon() -> str:
    # Play wants a full square without transparency; it applies its own rounded mask.
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 14 80 80">'
            f'<rect x="0" y="0" width="108" height="108" fill="{CHARCOAL}"/>{MARK}</svg>')


def feature_graphic() -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
  <defs>
    <radialGradient id="glow" cx="30%" cy="45%" r="75%">
      <stop offset="0" stop-color="#25262C"/>
      <stop offset="1" stop-color="{CHARCOAL}"/>
    </radialGradient>
  </defs>
  <rect width="1024" height="500" fill="url(#glow)"/>
  <g transform="translate(96,118) scale(2.4)">{MARK}</g>
  <text x="372" y="262" font-family="Montserrat" font-weight="300" font-size="92" letter-spacing="30"
        fill="{CHAMPAGNE}">VEYRA</text>
  <text x="376" y="324" font-family="Montserrat" font-weight="400" font-size="30" letter-spacing="3"
        fill="{IVORY}" fill-opacity="0.78">Your Music, Your Way.</text>
</svg>"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render(legacy_icon(False), (px, px), RES / f"mipmap-{density}/ic_launcher.webp")
        render(legacy_icon(True), (px, px), RES / f"mipmap-{density}/ic_launcher_round.webp")
    render(store_icon(), (512, 512), OUT / "icon-512.png")
    render(feature_graphic(), (1024, 500), OUT / "feature-graphic-1024x500.png")
    # Previews of the legacy icons, for review only.
    render(legacy_icon(False), (192, 192), OUT / "preview-legacy-192.png")
    render(legacy_icon(True), (192, 192), OUT / "preview-legacy-round-192.png")


if __name__ == "__main__":
    main()
