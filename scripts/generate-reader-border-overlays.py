#!/usr/bin/env python3
"""Generate deterministic high-resolution reader paper effect overlays.

The reader consumes the output as static raster assets. The script is only a
build-time maintenance helper, so no runtime texture generation or blur is
introduced on-device.
"""

from __future__ import annotations

import math
import random
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "composeApp" / "src" / "androidMain" / "assets" / "reader" / "paper-textures"

WIDTH = 3840
HEIGHT = 2160
EDGE_WEAR_RGB = (78, 58, 37)
EDGE_RIM_RGB = (238, 206, 146)
STAIN_RGB = (84, 66, 42)
GUTTER_SHADOW_RGB = (48, 38, 28)
GUTTER_HIGHLIGHT_RGB = (242, 212, 158)

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

    x = np.arange(WIDTH, dtype=np.float32)
    y = np.arange(HEIGHT, dtype=np.float32)
    side = np.maximum(
        np.maximum(0.0, 1.0 - x / 72.0) * left_weight,
        np.maximum(0.0, 1.0 - (WIDTH - 1 - x) / 72.0) * right_weight,
    )[None, :]
    vertical = np.maximum(
        np.maximum(0.0, 1.0 - y / 58.0) * top_weight,
        np.maximum(0.0, 1.0 - (HEIGHT - 1 - y) / 62.0) * bottom_weight,
    )[:, None]
    corner = np.maximum(side, vertical) ** 1.55
    edge = np.maximum(np.maximum(side ** 1.55, vertical ** 1.85), corner * 0.38) * bias
    alpha = Image.fromarray(np.clip(edge * 70, 0, 82).astype(np.uint8), "L")

    fibers = noise_layer(int(config["seed"]), (WIDTH, HEIGHT), blur=1.1, strength=14, offset=101)
    fibers = fibers.point(lambda value: max(0, min(11, int((value - 126) * 0.17))))
    return ImageChops.lighter(alpha.filter(ImageFilter.GaussianBlur(2.2)), ImageChops.multiply(fibers, alpha))


def rim_alpha(config: dict[str, float], wear: Image.Image) -> Image.Image:
    x = np.arange(WIDTH, dtype=np.float32)
    y = np.arange(HEIGHT, dtype=np.float32)
    horizontal = np.minimum(x, WIDTH - 1 - x)[None, :]
    vertical = np.minimum(y, HEIGHT - 1 - y)[:, None]
    distance = np.minimum(horizontal, vertical)
    ridge = np.maximum(0.0, 1.0 - np.abs(distance - 11.0) / 13.0)
    outer = np.maximum(0.0, 1.0 - distance / 22.0)
    rim = Image.fromarray(np.clip((ridge * 44 + outer * 18) * float(config["bias"]), 0, 58).astype(np.uint8), "L")
    return ImageChops.multiply(rim.filter(ImageFilter.GaussianBlur(0.55)), wear.point(lambda value: 255 if value > 2 else 0))


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
    center = (WIDTH - 1) / 2.0 + rng.uniform(-9, 9)

    phase = rng.uniform(0, math.tau)
    x = np.arange(WIDTH, dtype=np.float32)[None, :]
    y = np.arange(HEIGHT, dtype=np.float32)[:, None]
    vertical_bias = 0.92 + 0.08 * np.sin((y / HEIGHT) * math.tau + phase)
    crease = np.maximum(0.0, 1.0 - np.abs(x - center) / 9.0) * 46
    shadow_left = np.maximum(0.0, 1.0 - np.abs(x - (center - 34)) / 74.0) * 22
    shadow_right = np.maximum(0.0, 1.0 - np.abs(x - (center + 36)) / 80.0) * 20
    ridge = np.maximum(0.0, 1.0 - np.abs(x - (center + 7)) / 18.0) * 13
    alpha = Image.fromarray(np.clip(np.maximum.reduce((crease, shadow_left, shadow_right, ridge)) * vertical_bias, 0, 74).astype(np.uint8), "L")

    fibers = noise_layer(seed, (WIDTH, HEIGHT), blur=0.9, strength=10, offset=301)
    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(mask)
    draw.rectangle((int(center - 95), 0, int(center + 95), HEIGHT), fill=255)
    fiber_alpha = ImageChops.multiply(
        fibers.point(lambda value: max(0, min(9, int((value - 126) * 0.2)))),
        mask,
    )
    return ImageChops.lighter(alpha.filter(ImageFilter.GaussianBlur(2.2)), fiber_alpha)


def gutter_highlight_alpha(seed: int) -> Image.Image:
    rng = random.Random(seed + 53)
    center = (WIDTH - 1) / 2.0 + rng.uniform(-7, 7)
    phase = rng.uniform(-0.02, 0.02)
    x = np.arange(WIDTH, dtype=np.float32)[None, :]
    y = np.arange(HEIGHT, dtype=np.float32)[:, None]
    vertical_bias = 0.9 + 0.1 * np.sin((y / HEIGHT) * math.tau + phase)
    highlight = np.maximum(0.0, 1.0 - np.abs(x - (center + 12)) / 17.0) * 34
    soft = np.maximum(0.0, 1.0 - np.abs(x - (center + 48)) / 92.0) * 12
    alpha = Image.fromarray(np.clip(np.maximum(highlight, soft) * vertical_bias, 0, 46).astype(np.uint8), "L")
    return alpha.filter(ImageFilter.GaussianBlur(1.4))


FORCE_REGENERATE = "--force" in sys.argv


def save_overlay(name: str, alpha: Image.Image, rgb: tuple[int, int, int]) -> bool:
    target = OUTPUT_DIR / name
    if target.exists() and not FORCE_REGENERATE:
        return False
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), rgb + (0,))
    overlay.putalpha(alpha.filter(ImageFilter.GaussianBlur(0.2)))
    overlay.save(target, compress_level=1)
    return True


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for index, config in enumerate(EDGE_VARIANTS, start=1):
        seed = int(config["seed"])
        edge_targets = (
            OUTPUT_DIR / f"page-edge-overlay-{index:02d}.png",
            OUTPUT_DIR / f"page-edge-wear-overlay-{index:02d}.png",
            OUTPUT_DIR / f"page-edge-rim-overlay-{index:02d}.png",
            OUTPUT_DIR / f"page-stain-overlay-{index:02d}.png",
        )
        if not FORCE_REGENERATE and all(target.exists() for target in edge_targets):
            continue
        wear = edge_alpha(config)
        rim = rim_alpha(config, wear)
        wear_name = f"page-edge-wear-overlay-{index:02d}.png"
        save_overlay(wear_name, wear, EDGE_WEAR_RGB)
        legacy_edge = OUTPUT_DIR / f"page-edge-overlay-{index:02d}.png"
        if FORCE_REGENERATE or not legacy_edge.exists():
            shutil.copyfile(OUTPUT_DIR / wear_name, legacy_edge)
        save_overlay(f"page-edge-rim-overlay-{index:02d}.png", rim, EDGE_RIM_RGB)
        save_overlay(f"page-stain-overlay-{index:02d}.png", stain_alpha(seed), STAIN_RGB)
    for index, seed in enumerate(GUTTER_SEEDS, start=1):
        gutter_targets = (
            OUTPUT_DIR / f"spread-gutter-overlay-{index:02d}.png",
            OUTPUT_DIR / f"spread-gutter-highlight-overlay-{index:02d}.png",
        )
        if not FORCE_REGENERATE and all(target.exists() for target in gutter_targets):
            continue
        save_overlay(f"spread-gutter-overlay-{index:02d}.png", gutter_alpha(seed), GUTTER_SHADOW_RGB)
        save_overlay(f"spread-gutter-highlight-overlay-{index:02d}.png", gutter_highlight_alpha(seed), GUTTER_HIGHLIGHT_RGB)


if __name__ == "__main__":
    main()
