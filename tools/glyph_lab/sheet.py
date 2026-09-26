import sys, os, glob
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(__file__))
from simple import composite, S, INSET, END

BG, FG = (211, 227, 253), (8, 66, 160)
T = 120  # tile size

def crop(im): return im.crop((INSET, INSET, END, END)).resize((T, T), Image.LANCZOS)

def themed(mask):
    a = mask if isinstance(mask, Image.Image) else Image.fromarray(mask)
    a = a.convert('L') if a.mode != 'RGBA' else a.getchannel('A')
    plate = Image.new('RGB', (S, S), BG); plate.paste(Image.new('RGB', (S, S), FG), (0, 0), a)
    return crop(plate)

def old_mask(d):
    f = glob.glob(f'{d}/glyph_*.png')[0]
    return Image.open(f).convert('RGBA').getchannel('A')

if __name__ == '__main__':
    root, new, out = sys.argv[1], sys.argv[2], sys.argv[3]
    pkgs = sys.argv[4:] or sorted(os.path.basename(p)[:-4] for p in glob.glob(f'{new}/*.png'))
    cols = 3  # triples per row
    rows = (len(pkgs) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * (3 * T + 24), rows * (T + 16)), 'white')
    dr = ImageDraw.Draw(sheet)
    for i, p in enumerate(pkgs):
        x0 = (i % cols) * (3 * T + 24); y0 = (i // cols) * (T + 16)
        img = composite(f'{root}/{p}')
        orig = Image.fromarray(img.astype(np.uint8), 'RGBA')
        o = Image.new('RGB', (S, S), (200, 200, 200)); o.paste(orig, (0, 0), orig)
        sheet.paste(crop(o), (x0, y0))
        sheet.paste(themed(old_mask(f'{root}/{p}')), (x0 + T, y0))
        sheet.paste(themed(Image.open(f'{new}/{p}.png')), (x0 + 2 * T, y0))
        dr.text((x0, y0 + T + 2), p[:44], fill='black')
    sheet.save(out)
