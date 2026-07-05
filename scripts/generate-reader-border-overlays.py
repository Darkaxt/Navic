#!/usr/bin/env python3
"""Generate deterministic high-resolution reader paper effect overlays.

The reader consumes the output as static raster assets. The script is only a
build-time maintenance helper, so no runtime texture generation or blur is
introduced on-device.
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "composeApp" / "src" / "androidMain" / "assets" / "reader" / "paper-textures"

WIDTH = 3840
HEIGHT = 2160
OVERLAY_RGB = (70, 64, 58)

EDGE_VARIANTS = (
    {"seed": 4217, "left": 1.10, "right": 0.86, "top": 0.62, "bottom": 0.82, "bias": 0.96},
    {"seed": 9131, "left": 0.88, "right": 1.08, "top": 0.74, "bottom": 0.78, "bias": 1.00},
    {"seed": 2503, "left": 1.02, "right": 0.92, "top": 0.68, "bottom": 0.94, "bias": 0.98},
    {"seed": 7819, "left": 0.95, "right": 1.05, "top": 0.80, "bottom": 0.74, "bias": 1.02},
    {"seed": 6151, "left": 1.04, "right": 0.98, "top": 0.58, "bottom": 0.90, "bias": 1.00},
    {"seed": 1087, "left": 0.91, "right": 1.12, "top": 0.72, "bottom": 0.84, "bias": 0.97},
    {"seed": 3499, "left": 1.14, "right": 0.90, "top": 0.86, "bottom": 0.72, "bias": 1.03},
    {"seed": 8849, "left": 0.97, "right": 1.00, "top": 0.66, "bottom": 0.98, "bias": 0.99},
)

GUTTER_SEEDS = (2311, 5693, 7877, 9281)


def noise_layer(seed: int, size: tuple[int, int], blur: float, strength: float, offset: int = 0) -> Image.Image:
    rng = random.Random(seed + offset)
    noise = Image.effect_noise(size, strength).convert("L")
    if rng.choice((False, True)):
        noise = noise.rotate(180)
    return noise.filter(ImageFilter.GaussianBlur(blur))


def edge_alpha(config: dict[str, float]) -> Image.Image:
    left_weight = float(config["left"])
    right_weight = float(config["right"])
    top_weight = float(config["top"])
    bottom_weight = float(config["bottom"])
    bias = float(config["bias"])

    alpha = Image.new("L", (WIDTH, HEIGHT), 0)
    pixels = alpha.load()

    for y in range(HEIGHT):
        top = max(0.0, 1.0 - y / 390.0) * top_weight
        bottom = max(0.0, 1.0 - (HEIGHT - 1 - y) / 430.0) * bottom_weight
        vertical = max(top, bottom)
        for x in range(WIDTH):
            left = max(0.0, 1.0 - x / 255.0) * left_weight
            right = max(0.0, 1.0 - (WIDTH - 1 - x) / 255.0) * right_weight
            side = max(left, right)
            corner = max(side, vertical) ** 1.55
            edge = max(side ** 1.8, vertical ** 2.05, corner * 0.48) * bias
            pixels[x, y] = min(66, int(edge * 64))

    fibers = noise_layer(int(config["seed"]), (WIDTH, HEIGHT), blur=1.1, strength=14, offset=101)
    fibers = fibers.point(lambda value: max(0, min(11, int((value - 126) * 0.17))))
    return ImageChops.lighter(alpha.filter(ImageFilter.GaussianBlur(13)), ImageChops.multiply(fibers, alpha))


def stain_alpha(seed: int) -> Image.Image:
    base = noise_layer(seed, (WIDTH, HEIGHT), blur=40, strength=32, offset=11)
    large = noise_layer(seed, (WIDTH, HEIGHT), blur=96, strength=58, offset=23)
    base_alpha = base.point(lambda value: max(0, min(18, int((value - 116) * 0.16))))
    large_alpha = large.point(lambda value: max(0, min(14, int((value - 118) * 0.10))))
    alpha = ImageChops.lighter(base_alpha, large_alpha)

    rng = random.Random(seed + 37)
    dust_mask = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(dust_mask)
    for _ in range(120):
        x = int(rng.uniform(0, WIDTH))
        y = int(rng.uniform(0, HEIGHT))
        radius = rng.uniform(0.8, 3.6)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=rng.randint(5, 18))
    for _ in range(24):
        x = int(rng.uniform(0, WIDTH))
        y = int(rng.uniform(0, HEIGHT))
        length = rng.uniform(18, 54)
        angle = rng.uniform(0, math.tau)
        draw.line((x, y, x + math.cos(angle) * length, y + math.sin(angle) * length), fill=rng.randint(4, 12), width=1)
    return ImageChops.lighter(alpha, dust_mask.filter(ImageFilter.GaussianBlur(0.55)))


def gutter_alpha(seed: int) -> Image.Image:
    rng = random.Random(seed)
    alpha = Image.new("L", (WIDTH, HEIGHT), 0)
    pixels = alpha.load()
    center = (WIDTH - 1) / 2.0 + rng.uniform(-9, 9)

    phase = rng.uniform(0, math.tau)
    for y in range(HEIGHT):
        vertical_bias = 0.92 + 0.08 * math.sin((y / HEIGHT) * math.tau + phase)
        for x in range(WIDTH):
            d = abs(x - center)
            crease = max(0.0, 1.0 - d / 9.0) * 46
            shadow_left = max(0.0, 1.0 - abs(x - (center - 34)) / 74.0) * 22
            shadow_right = max(0.0, 1.0 - abs(x - (center + 36)) / 80.0) * 20
            ridge = max(0.0, 1.0 - abs(x - (center + 7)) / 18.0) * 13
            pixels[x, y] = min(74, int(max(crease, shadow_left, shadow_right, ridge) * vertical_bias))

    fibers = noise_layer(seed, (WIDTH, HEIGHT), blur=0.9, strength=10, offset=301)
    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(mask)
    draw.rectangle((int(center - 95), 0, int(center + 95), HEIGHT), fill=255)
    fiber_alpha = ImageChops.multiply(
        fibers.point(lambda value: max(0, min(9, int((value - 126) * 0.2)))),
        mask,
    )
    return ImageChops.lighter(alpha.filter(ImageFilter.GaussianBlur(2.2)), fiber_alpha)


def save_overlay(name: str, alpha: Image.Image) -> None:
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), OVERLAY_RGB + (0,))
    overlay.putalpha(alpha.filter(ImageFilter.GaussianBlur(0.2)))
    overlay.save(OUTPUT_DIR / name, optimize=True)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for index, config in enumerate(EDGE_VARIANTS, start=1):
        seed = int(config["seed"])
        save_overlay(f"page-edge-overlay-{index:02d}.png", edge_alpha(config))
        save_overlay(f"page-stain-overlay-{index:02d}.png", stain_alpha(seed))
    for index, seed in enumerate(GUTTER_SEEDS, start=1):
        save_overlay(f"spread-gutter-overlay-{index:02d}.png", gutter_alpha(seed))


if __name__ == "__main__":
    main()
