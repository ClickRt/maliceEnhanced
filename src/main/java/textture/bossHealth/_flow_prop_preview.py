"""Preview the proportional normal-state vein flow: 5 heads spread over the lit length.

Mirrors UndefinedWitherBossBar.blitFlowAnimated:
  step    = flowLength / FLOW_BANDS
  head_b  = VEIN_X + (b + phase) * step
  headW   = max(2, step*0.16)   tailLen = max(4, step*0.40)
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
FLOW_BANDS = 5
HEAD_FRAC, TAIL_FRAC = 0.16, 0.40
TAIL_CHUNKS = 5
TAIL_RGB, TAIL_A = (0.55, 0.90, 1.00), 0.55
HEAD_RGB, HEAD_A = (0.85, 1.00, 1.00), 0.85


def tinted(canvas, x0, y, w, h, rgb, a):
    if w <= 0:
        return
    x0 = max(0, min(TEX_W - 1, x0))
    w = min(w, TEX_W - x0)
    patch = FILL.crop((x0, y, x0 + w, y + h)).convert("RGBA")
    arr = np.array(patch).astype(float)
    arr[:, :, 0] *= rgb[0]
    arr[:, :, 1] *= rgb[1]
    arr[:, :, 2] *= rgb[2]
    arr[:, :, 3] *= a
    canvas.alpha_composite(Image.fromarray(arr.astype(np.uint8)), (x0, y))


def render(filled, phase):
    c = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    for bx, bw in BANDS:
        lit = max(0, min(bw, filled - (bx - VEIN_X)))
        if lit > 0:
            tinted(c, bx, VEIN_Y, lit, VEIN_H, EMPTY, 1.0)

    flow_len = max(0, min(filled, VEIN_W))
    if flow_len > 0:
        step = flow_len / FLOW_BANDS
        headW = max(2.0, step * HEAD_FRAC)
        tailLen = max(4.0, step * TAIL_FRAC)
        tailStep = tailLen / TAIL_CHUNKS
        for b in range(FLOW_BANDS):
            head = VEIN_X + (b + phase) * step
            for bx, bw in BANDS:
                lit = max(0, min(bw, filled - (bx - VEIN_X)))
                if lit <= 0:
                    continue
                lo, hi = bx, bx + lit
                for ch in range(TAIL_CHUNKS):
                    a0 = round(head - tailLen + ch * tailStep)
                    a1 = round(head - tailLen + (ch + 1) * tailStep)
                    k = (ch + 1) / TAIL_CHUNKS
                    f, t = max(a0, lo), min(a1, hi)
                    if t > f:
                        tinted(c, f, VEIN_Y, t - f, VEIN_H, TAIL_RGB, TAIL_A * k * k)
                f = max(round(head), lo)
                t = min(round(head + headW), hi)
                if t > f:
                    tinted(c, f, VEIN_Y, t - f, VEIN_H, HEAD_RGB, HEAD_A)

    c.alpha_composite(FRAME)
    body = c.crop((0, CROP_TOP, TEX_W, CROP_BOTTOM))
    return body.resize((round(TEX_W * SCALE), round((CROP_BOTTOM - CROP_TOP) * SCALE)),
                       Image.LANCZOS)


PHASES = [0.0, 0.2, 0.4, 0.6, 0.8]
RATIOS = [("100%", VEIN_W), ("60%", round(VEIN_W * 0.6)), ("30%", round(VEIN_W * 0.3))]

frames_w = None
rows = []
for label, filled in RATIOS:
    row = [render(filled, p) for p in PHASES]
    frames_w = row[0].width
    rows.append((label, row))

PAD, GAP = 8, 4
cols = len(PHASES)
rowh = rows[0][1][0].height
W = PAD * 2 + cols * frames_w + (cols - 1) * GAP
H = PAD * 2 + len(rows) * rowh + (len(rows) - 1) * (GAP + 16)
sheet = Image.new("RGB", (W, H), (22, 22, 26))
for r, (label, row) in enumerate(rows):
    y = PAD + r * (rowh + GAP + 16)
    for c, im in enumerate(row):
        sheet.paste(im, (PAD + c * (frames_w + GAP), y))
sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
sheet.save("_flow_prop_preview.jpg", quality=95)
print("saved _flow_prop_preview.jpg", sheet.size, "frame", frames_w, "x", rowh)

for label, filled in RATIOS:
    step = filled / FLOW_BANDS
    print(f"{label}: filled={filled}px  step={step:.1f}  headW={max(2, step*HEAD_FRAC):.1f}"
          f"  tail={max(4, step*TAIL_FRAC):.1f}")
