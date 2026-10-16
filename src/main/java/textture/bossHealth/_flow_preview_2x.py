"""Preview the normal-state vein flow animation (blitFlowAnimated) as a strip of frames.

Mirrors UndefinedWitherBossBar: empty veil -> flow head+tail -> frame on top,
composited at SCALE=0.45 so the strip matches on-screen size (230x30 body).
"""
from PIL import Image
import numpy as np

BASE = (r"F:\admx\newFile\forge-1.20.1-47.4.10-mdkA\src\main\resources\assets"
        r"\malice_enhanced\textures\gui\boss_bar")
FRAME = Image.open(BASE + r"\undefined_wither.png").convert("RGBA")
FILL = Image.open(BASE + r"\undefined_wither_fill.png").convert("RGBA")

TEX_W, TEX_H = 512, 188
CROP_TOP, CROP_BOTTOM = 60, 126
VEIN_Y, VEIN_H = 88, 10
VEIN_X, VEIN_W = 102, 320
BANDS = [(102, 140), (270, 152)]
SCALE = 0.45

EMPTY = (0.16, 0.18, 0.30)
FLOW_W, FLOW_SPACING = 10, 60
TAIL_STEP, TAIL_CHUNKS = 5, 5
TAIL_RGB, TAIL_A = (0.55, 0.90, 1.00), 0.55
HEAD_RGB, HEAD_A = (0.85, 1.00, 1.00), 0.85


def layer():
    return Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))


def tinted(canvas, x0, y, w, h, rgb, a):
    """Blit FILL region [x0,x0+w) x [y,y+h) with shader color (rgb,a)."""
    if w <= 0:
        return
    patch = FILL.crop((x0, y, x0 + w, y + h)).convert("RGBA")
    arr = np.array(patch).astype(float)
    arr[:, :, 0] *= rgb[0]
    arr[:, :, 1] *= rgb[1]
    arr[:, :, 2] *= rgb[2]
    arr[:, :, 3] *= a
    tinted_patch = Image.fromarray(arr.astype(np.uint8))
    canvas.alpha_composite(tinted_patch, (x0, y))


def frame(filled, phase_px):
    c = layer()
    # empty veil across lit width, then flow bands on top
    for bx, bw in BANDS:
        lit = max(0, min(bw, filled - (bx - VEIN_X)))
        if lit > 0:
            tinted(c, bx, VEIN_Y, lit, VEIN_H, EMPTY, 1.0)

    flow_len = max(0, min(filled, VEIN_W))
    tail = TAIL_STEP * TAIL_CHUNKS
    head_x = VEIN_X - tail + phase_px
    while head_x < VEIN_X + flow_len:
        for bx, bw in BANDS:
            lit = max(0, min(bw, filled - (bx - VEIN_X)))
            if lit <= 0:
                continue
            lo, hi = bx, bx + lit
            for ch in range(TAIL_CHUNKS):
                a0 = head_x - tail + ch * TAIL_STEP
                a1 = a0 + TAIL_STEP
                k = (ch + 1) / TAIL_CHUNKS
                f, t = max(a0, lo), min(a1, hi)
                if t > f:
                    tinted(c, f, VEIN_Y, t - f, VEIN_H, TAIL_RGB, TAIL_A * k * k)
            f, t = max(head_x, lo), min(head_x + FLOW_W, hi)
            if t > f:
                tinted(c, f, VEIN_Y, t - f, VEIN_H, HEAD_RGB, HEAD_A)
        head_x += FLOW_SPACING

    c.alpha_composite(FRAME)
    body = c.crop((0, CROP_TOP, TEX_W, CROP_BOTTOM))
    return body.resize((round(TEX_W * SCALE), round((CROP_BOTTOM - CROP_TOP) * SCALE)),
                       Image.LANCZOS)


steps = 12
frames = [frame(filled=VEIN_W, phase_px=round(i * FLOW_SPACING / steps)) for i in range(steps)]

PAD, GAP = 6, 4
w = frames[0].width
h = frames[0].height
strip = Image.new("RGB", (PAD * 2 + w, PAD * 2 + steps * (h + GAP) - GAP), (24, 24, 28))
for i, f in enumerate(frames):
    strip.paste(f, (PAD, PAD + i * (h + GAP)))
strip = strip.resize((strip.width * 3, strip.height * 3), Image.NEAREST)
strip.save("_flow_preview_2x.jpg", quality=95)
print("saved _flow_preview_2x.jpg", strip.size, "frame", w, "x", h)

# half-progress single frame, big
half = frame(filled=VEIN_W // 2, phase_px=20)
big = half.resize((half.width * 4, half.height * 4), Image.NEAREST)
big.convert("RGB").save("_flow_half_4x.jpg", quality=95)
print("saved _flow_half_4x.jpg", big.size)
