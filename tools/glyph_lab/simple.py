"""Simple split: vector icons keyed per pixel against the background layer; image icons keyed
against the colours seen on the viewport edge."""
import sys, os, glob
import numpy as np
from PIL import Image
S = 432; INSET = S // 6; END = S - INSET; VP = END - INSET
TARGET = 0.66
EDGE_SUPPORT = 0.15   # an edge colour is background if >= 15% of the ring is close to it
EDGE_RADIUS = float(os.environ.get('EDGE_RADIUS', 48))
LO, HI = 24.0, 64.0     # colour distance: <= LO → background, >= HI → glyph

def smooth(d):
    t = np.clip((d - LO) / (HI - LO), 0, 1); return t * t * (3 - 2 * t)

def rgba(path): return np.array(Image.open(path).convert('RGBA')).astype(np.float32)

def is_vector(d):
    """Heuristic stand-in for drawable classes: few exact colours cover almost everything."""
    for n in ('bg', 'fg', 'legacy'):
        p = f'{d}/{n}.png'
        if not os.path.exists(p): continue
        a = rgba(p); op = a[..., 3] >= 250
        if op.sum() == 0: continue
        cols = a[op][:, :3].astype(int); keys = cols[:, 0] * 65536 + cols[:, 1] * 256 + cols[:, 2]
        _, counts = np.unique(keys, return_counts=True)
        top = np.sort(counts)[::-1][:6].sum() / len(keys)
        if top < 0.85: return False
    return not os.path.exists(f'{d}/legacy.png') or True

def ring_pixels(img, off):
    a, b = INSET + off, END - 1 - off
    pts = [(a, x) for x in range(a, b)] + [(y, b) for y in range(a, b)] + [(b, x) for x in range(b, a, -1)] + [(y, a) for y in range(b, a, -1)]
    return np.array([img[y, x] for y, x in pts])

def edge_palette(img):
    """Colours seen on the viewport edge (stepping inwards past transparent corners); a colour
    counts if at least 5% of the ring is close to it, so a logo touching the edge is ignored."""
    for off in range(2, VP // 2, 6):
        ring = ring_pixels(img, off)
        opaque = ring[ring[:, 3] >= 250][:, :3]
        if len(opaque) >= len(ring) // 4: break
    else:
        return np.zeros((0, 3))
    d = np.linalg.norm(opaque[:, None, :] - opaque[None, :, :], axis=2)
    support = (d < EDGE_RADIUS).sum(axis=1) / len(ring)
    return opaque[support >= EDGE_SUPPORT]

def min_dist(rgb, palette):
    if len(palette) == 0: return np.full(rgb.shape[:2], 1e9)
    pal = np.unique((palette // 4).astype(int), axis=0) * 4 + 2
    flat = rgb.reshape(-1, 3); best = np.full(flat.shape[0], 1e9, np.float32)
    for c in pal: best = np.minimum(best, np.linalg.norm(flat - c, axis=1))
    return best.reshape(rgb.shape[:2])

def viewport(a):
    out = np.zeros_like(a); out[INSET:END, INSET:END] = a[INSET:END, INSET:END]; return out

def coverage(a): return (a[INSET:END, INSET:END] > 0.5).mean()

def composite(d):
    """Adaptive: bg + fg over black. Legacy: drawn into the viewport on transparent."""
    if os.path.exists(f'{d}/legacy.png'):
        leg = Image.open(f'{d}/legacy.png').convert('RGBA').resize((VP, VP), Image.LANCZOS)
        img = Image.new('RGBA', (S, S), (0, 0, 0, 0)); img.paste(leg, (INSET, INSET), leg)
        return np.array(img).astype(np.float32)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 255))
    for n in ('bg', 'fg'):
        if os.path.exists(f'{d}/{n}.png'): im.alpha_composite(Image.open(f'{d}/{n}.png').convert('RGBA'))
    return np.array(im).astype(np.float32)

def layer_palette(d):
    """Colours of the background layer: a vector background only has a few."""
    if not os.path.exists(f'{d}/bg.png'): return np.array([[0, 0, 0]], np.float32)
    bg = rgba(f'{d}/bg.png'); rgb = bg[..., :3] * (bg[..., 3:] / 255)
    v = rgb[INSET:END, INSET:END].reshape(-1, 3)
    q = (v // 4).astype(int); keys = q[:, 0] * 65536 + q[:, 1] * 256 + q[:, 2]
    u, inv, counts = np.unique(keys, return_inverse=True, return_counts=True)
    keep = counts >= 0.01 * len(v)
    return np.array([v[inv == i].mean(axis=0) for i in np.where(keep)[0]])

def keyed(img, palette):
    return viewport(img[..., 3] / 255 * smooth(min_dist(img[..., :3], palette)))

def erode(m, r):
    for _ in range(r):
        o = m.copy(); o[1:] &= m[:-1]; o[:-1] &= m[1:]; o[:, 1:] &= m[:, :-1]; o[:, :-1] &= m[:, 1:]; m = o
    return m

PLATE = 0.6

def dominant_color(img, a):
    px = img[..., :3][a > 0.5]
    q = (px // 16).astype(int); keys = q[:, 0] * 256 + q[:, 1] * 16 + q[:, 2]
    k = np.bincount(keys).argmax(); return px[keys == k].mean(axis=0)

def unplate(img, a, palette):
    """The glyph is really a plate: key out its own main colour too."""
    if coverage(a) <= PLATE: return a
    b = keyed(img, np.vstack([palette, dominant_color(img, a)[None]]))
    return b if coverage(b) >= 0.03 else a      # nothing left without the plate: keep it

def vector_glyph(d):
    img = composite(d)
    return unplate(img, keyed(img, layer_palette(d)), np.vstack([layer_palette(d), edge_palette(img)]))

def image_glyph(d):
    img = composite(d)
    alpha = img[..., 3]
    if (alpha[INSET:END, INSET:END] >= 250).mean() >= PLATE:
        # legacy icon with its own plate: stay inside the plate, off its anti-aliased rim
        img[..., 3] = np.where(erode(alpha >= 250, max(1, S // 100)), 255, 0)
    pal = edge_palette(img)
    return unplate(img, keyed(img, pal), pal)

def luminance(d):
    im = Image.new('RGBA', (S, S), (0, 0, 0, 255))
    for n in ('bg', 'fg', 'legacy'):
        if os.path.exists(f'{d}/{n}.png'): im.alpha_composite(Image.open(f'{d}/{n}.png').convert('RGBA'))
    g = np.array(im)[..., :3].astype(np.float32).mean(axis=2)
    v = g[INSET:END, INSET:END]; mn, mx = v.min(), v.max()
    if mx <= mn: return None
    st = (g - mn) / (mx - mn)
    ring = ring_pixels(st[..., None], 4)[:, 0]
    if ring.mean() > 0.5: st = 1 - st
    return viewport(st)

def dilate(m, r):
    for _ in range(r):
        o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]; m = o
    return m

def finish(a):
    if a is None: return None
    r = max(1, S // 216)
    a = a * dilate(erode(a > 0.5, r), r + 1)      # drop thin remnants (anti-aliased outlines)
    m = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)); arr = np.array(m)
    ys, xs = np.where(arr >= 40)
    if len(xs) == 0: return None
    w, h = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
    s = min(max(TARGET * VP / max(w, h), 0.5), 2.5)
    cx, cy = (xs.max() + xs.min() + 1) / 2, (ys.max() + ys.min() + 1) / 2
    return m.transform((S, S), Image.AFFINE, (1 / s, 0, cx - S / 2 / s, 0, 1 / s, cy - S / 2 / s), Image.BILINEAR)

def extract(d):
    kind = 'vector' if (os.path.exists(f'{d}/fg.png') and is_vector(d)) else 'image'
    a = vector_glyph(d) if kind == 'vector' else image_glyph(d)
    cov = coverage(a)
    if cov < 0.005 or cov > PLATE + 0.1:
        a = luminance(d); kind += '+lum'
    return finish(a), kind

if __name__ == '__main__':
    root, outdir = sys.argv[1], sys.argv[2]; os.makedirs(outdir, exist_ok=True)
    pkgs = sys.argv[3:] or sorted(os.path.basename(p) for p in glob.glob(f'{root}/*') if glob.glob(f'{p}/glyph_FORCED*') or glob.glob(f'{p}/glyph_FAILED*'))
    kinds = {}
    for p in pkgs:
        r, k = extract(f'{root}/{p}'); kinds[p] = k
        (r or Image.new('L', (S, S), 0)).save(f'{outdir}/{p}.png')
    open(f'{outdir}/kinds.txt', 'w').write('\n'.join(f'{p} {k}' for p, k in kinds.items()))
    from collections import Counter; print(Counter(kinds.values()))
