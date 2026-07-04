#!/usr/bin/env python3
"""Generate deterministic reader page border degradation overlays.

The overlays are intentionally procedural. AI-generated "transparent" source
images can bake the preview checkerboard into the pixels, which then becomes
visible in the reader. These masks use only alpha gradients, blurred noise, and
sparse dust over a transparent background.
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "composeApp" / "src" / "androidMain" / "assets" / "reader" / "paper-textures"

WIDTH = 1086
HEIGHT = 1448
OVERLAY_RGB = (70, 64, 58)

VARIANTS = (
    {"seed": 4217, "left": 1.10, "right": 0.86, "top": 0.70, "bottom": 0.96, "bias": 0.96},
    {"seed": 9131, "left": 0.88, "right": 1.08, "top": 0.82, "bottom": 0.90, "bias": 1.00},
    {"seed": 2503, "left": 1.02, "right": 0.92, "top": 0.76, "bottom": 1.06, "bias": 0.98},
    {"seed": 7819, "left": 0.95, "right": 1.05, "top": 0.88, "bottom": 0.86, "bias": 1.02},
)


def noise_layer(seed: int, blur: float, strength: float, offset: int = 0) -> Image.Image:
    random.seed(seed + offset)
    noise = Image.effect_noise((WIDTH, HEIGHT), strength).convert("L")
    angle = random.choice((0, 90, 180, 270))
    if angle:
        noise = noise.rotate(angle)
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
        top = max(0.0, 1.0 - y / 240.0) * top_weight
        bottom = max(0.0, 1.0 - (HEIGHT - 1 - y) / 280.0) * bottom_weight
        vertical = max(top, bottom)

        for x in range(WIDTH):
            left = max(0.0, 1.0 - x / 150.0) * left_weight
            right = max(0.0, 1.0 - (WIDTH - 1 - x) / 150.0) * right_weight
            side = max(left, right)
            corner = max(side, vertical) ** 1.45
            edge = max(side ** 1.72, vertical ** 1.92, corner * 0.58) * bias
            pixels[x, y] = min(42, int(edge * 42))

    return alpha.filter(ImageFilter.GaussianBlur(18))


def stains(seed: int) -> Image.Image:
    base = noise_layer(seed, blur=32, strength=28, offset=11)
    large = noise_layer(seed, blur=72, strength=52, offset=23)
    merged = ImageChops.multiply(base, large)

    edge = edge_alpha({"left": 1.0, "right": 1.0, "top": 0.75, "bottom": 0.95, "bias": 1.0})
    stains_alpha = ImageChops.multiply(merged, edge).point(lambda value: max(0, min(22, int((value - 105) * 0.18))))
    return stains_alpha.filter(ImageFilter.GaussianBlur(9))


def dust(seed: int) -> Image.Image:
    rng = random.Random(seed + 37)
    dust_mask = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(dust_mask)

    for _ in range(42):
        edge_bias = rng.random()
        if edge_bias < 0.42:
            x = int(rng.choice((rng.uniform(0, 130), rng.uniform(WIDTH - 130, WIDTH))))
            y = int(rng.uniform(0, HEIGHT))
        elif edge_bias < 0.72:
            x = int(rng.uniform(0, WIDTH))
            y = int(rng.choice((rng.uniform(0, 150), rng.uniform(HEIGHT - 170, HEIGHT))))
        else:
            x = int(rng.uniform(0, WIDTH))
            y = int(rng.uniform(0, HEIGHT))

        radius = rng.uniform(0.7, 2.3)
        alpha = rng.randint(7, 18)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=alpha)

    for _ in range(14):
        x = int(rng.uniform(0, WIDTH))
        y = int(rng.uniform(0, HEIGHT))
        length = rng.uniform(7, 20)
        angle = rng.uniform(0, math.tau)
        alpha = rng.randint(5, 12)
        draw.line((x, y, x + math.cos(angle) * length, y + math.sin(angle) * length), fill=alpha, width=1)

    return dust_mask.filter(ImageFilter.GaussianBlur(0.45))


def save_overlay(index: int, prefix: str, alpha: Image.Image) -> None:
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), OVERLAY_RGB + (0,))
    overlay.putalpha(alpha.filter(ImageFilter.GaussianBlur(0.35)))
    overlay.save(OUTPUT_DIR / f"{prefix}-{index}.png", optimize=True)


def generate_variant(index: int, config: dict[str, float]) -> None:
    seed = int(config["seed"])
    edge = edge_alpha(config)
    stain = ImageChops.lighter(stains(seed), dust(seed))
    combined = ImageChops.lighter(edge, stain)

    save_overlay(index, "page-edge-overlay", edge)
    save_overlay(index, "page-stain-overlay", stain)
    save_overlay(index, "page-border-overlay", combined)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for index, config in enumerate(VARIANTS, start=1):
        generate_variant(index, config)


if __name__ == "__main__":
    main()
