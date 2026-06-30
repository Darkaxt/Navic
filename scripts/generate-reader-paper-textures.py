from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"C:\Users\darka\Pictures\Resource Boy - Kraft Paper Textures\05.jpg")
OUTPUT_DIR = ROOT / "composeApp" / "src" / "androidMain" / "assets" / "reader" / "paper-textures"
OUTPUT_SIZE = (2880, 4096)
JPEG_QUALITY = 84


def centered_crop_box(width: int, height: int, aspect: float) -> tuple[int, int, int, int]:
    source_aspect = width / height
    if source_aspect > aspect:
        crop_width = round(height * aspect)
        left = (width - crop_width) // 2
        return left, 0, left + crop_width, height
    crop_height = round(width / aspect)
    top = (height - crop_height) // 2
    return 0, top, width, top + crop_height


def grid_crop(source: Image.Image, column: int, row: int) -> Image.Image:
    width, height = source.size
    cell_width = width // 3
    cell_height = height // 3
    left = column * cell_width
    top = row * cell_height
    right = width if column == 2 else left + cell_width
    bottom = height if row == 2 else top + cell_height
    cell = source.crop((left, top, right, bottom))
    crop_box = centered_crop_box(cell.width, cell.height, OUTPUT_SIZE[0] / OUTPUT_SIZE[1])
    return cell.crop(crop_box)


def main() -> None:
    if not SOURCE.is_file():
        raise SystemExit(f"Missing source texture: {SOURCE}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with Image.open(SOURCE) as image:
        source = ImageOps.exif_transpose(image).convert("RGB")
        index = 1
        for row in range(3):
            for column in range(3):
                crop = grid_crop(source, column, row)
                resized = crop.resize(OUTPUT_SIZE, Image.Resampling.LANCZOS)
                out = OUTPUT_DIR / f"paper-texture-{index:02d}.jpg"
                resized.save(out, "JPEG", quality=JPEG_QUALITY, optimize=True, progressive=True)
                index += 1


if __name__ == "__main__":
    main()
