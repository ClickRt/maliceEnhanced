"""32x32 pixel-art extrusion, one cube per opaque texel.

Vanilla generated items do this at 16x16. The soul bow textures are now
32x32, so each pixel is 0.5 model units wide and 1 unit thick (7.5-8.5).
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX_DIR = ROOT / "src" / "main" / "resources" / "assets" / "malice_enhanced" / "textures" / "item"
MODEL_DIR = ROOT / "src" / "main" / "resources" / "assets" / "malice_enhanced" / "models" / "item"

GRID = 32
Z0, Z1 = 7.5, 8.5
ALPHA_CUTOFF = 128
DISPLAY = {
    "thirdperson_righthand": {
        "rotation": [-80, 260, -40],
        "translation": [-1, -2, 2.5],
        "scale": [0.9, 0.9, 0.9],
    },
    "thirdperson_lefthand": {
        "rotation": [-80, -280, 40],
        "translation": [-1, -2, 2.5],
        "scale": [0.9, 0.9, 0.9],
    },
    "firstperson_righthand": {
        "rotation": [0, -90, 25],
        "translation": [1.13, 3.2, 1.13],
        "scale": [0.68, 0.68, 0.68],
    },
    "firstperson_lefthand": {
        "rotation": [0, 90, -25],
        "translation": [1.13, 3.2, 1.13],
        "scale": [0.68, 0.68, 0.68],
    },
    "ground": {
        "rotation": [0, 0, 0],
        "translation": [0, 2, 0],
        "scale": [0.5, 0.5, 0.5],
    },
    "head": {
        "rotation": [0, 180, 0],
        "translation": [0, 13, 7],
        "scale": [1, 1, 1],
    },
    "fixed": {
        "rotation": [0, 180, 0],
        "scale": [1, 1, 1],
    },
}

FRAMES = [
    "soul_bow",
    "soul_bow_pulling_0",
    "soul_bow_pulling_1",
    "soul_bow_pulling_2",
]


def ensure_rgba(path: Path) -> Image.Image:
    im = Image.open(path)
    if im.mode != "RGBA":
        im = im.convert("RGBA")
        px = im.load()
        w, h = im.size
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a == 0:
                    px[x, y] = (0, 0, 0, 0)
        im.save(path, format="PNG")
    return im


def occupancy(im: Image.Image) -> np.ndarray:
    arr = np.array(im.convert("RGBA"))
    if arr.shape[0] != GRID or arr.shape[1] != GRID:
        raise SystemExit(f"expected {GRID}x{GRID}, got {arr.shape[1]}x{arr.shape[0]}")
    return arr[:, :, 3] >= ALPHA_CUTOFF


def cell_uv(gx: int, tex_row: int) -> list[float]:
    step = 16.0 / GRID
    return [
        round(gx * step, 4),
        round(tex_row * step, 4),
        round((gx + 1) * step, 4),
        round((tex_row + 1) * step, 4),
    ]


def elements_for(mask: np.ndarray) -> list[dict]:
    step = 16.0 / GRID
    elems = []
    for gy in range(GRID):
        for gx in range(GRID):
            tex_row = GRID - 1 - gy
            if not mask[tex_row, gx]:
                continue
            uvs = cell_uv(gx, tex_row)
            faces = {
                "south": {"uv": uvs, "texture": "#layer0"},
                "north": {
                    "uv": [uvs[2], uvs[1], uvs[0], uvs[3]],
                    "texture": "#layer0",
                },
            }
            if gx == 0 or not mask[tex_row, gx - 1]:
                faces["west"] = {"uv": uvs, "texture": "#layer0"}
            if gx == GRID - 1 or not mask[tex_row, gx + 1]:
                faces["east"] = {"uv": uvs, "texture": "#layer0"}
            if gy == 0 or not mask[GRID - gy, gx]:
                faces["down"] = {"uv": uvs, "texture": "#layer0", "rotation": 180}
            if gy == GRID - 1 or not mask[GRID - 2 - gy, gx]:
                faces["up"] = {"uv": uvs, "texture": "#layer0"}
            elems.append(
                {
                    "from": [round(gx * step, 4), round(gy * step, 4), Z0],
                    "to": [round((gx + 1) * step, 4), round((gy + 1) * step, 4), Z1],
                    "faces": faces,
                }
            )
    return elems


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    write_json(MODEL_DIR / "soul_bow_base.json", {"gui_light": "front", "display": DISPLAY})
    debug_dir = ROOT / "output" / "shengtu" / "bow_voxel_debug"
    debug_dir.mkdir(parents=True, exist_ok=True)

    for name in FRAMES:
        path = TEX_DIR / f"{name}.png"
        im = ensure_rgba(path)
        mask = occupancy(im)
        elems = elements_for(mask)
        model = {
            "parent": "malice_enhanced:item/soul_bow_base",
            "gui_light": "front",
            "display": DISPLAY,
            "textures": {
                "layer0": f"malice_enhanced:item/{name}",
                "particle": f"malice_enhanced:item/{name}",
            },
            "elements": elems,
        }
        if name == "soul_bow":
            model["overrides"] = [
                {"predicate": {"pulling": 1}, "model": "malice_enhanced:item/soul_bow_pulling_0"},
                {"predicate": {"pulling": 1, "pull": 0.65}, "model": "malice_enhanced:item/soul_bow_pulling_1"},
                {"predicate": {"pulling": 1, "pull": 0.9}, "model": "malice_enhanced:item/soul_bow_pulling_2"},
            ]
        write_json(MODEL_DIR / f"{name}.json", model)
        Image.fromarray((mask.astype(np.uint8) * 255), mode="L").resize(
            (256, 256), Image.Resampling.NEAREST
        ).save(debug_dir / f"{name}_mask.png")
        print(name, im.mode, im.size, "solid", int(mask.sum()), "elements", len(elems))


if __name__ == "__main__":
    main()
