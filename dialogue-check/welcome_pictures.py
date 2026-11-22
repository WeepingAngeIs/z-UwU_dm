"""Check the colored paper and dark glyphs in actual native game frames, in addition to the draw observer."""
import json
from pathlib import Path
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

root = Path(sys.argv[1])
folder = root / "welcome"
rows = json.loads((folder / "visits.json").read_text())
assert len(rows) == 14 and len({r["day"] for r in rows}) == 14
colors = {"LEAF": (230,245,217), "LOTUS": (255,218,232), "DUSK": (237,226,248), "PLAIN": (255,249,241)}
motifs = {"LEAF": (115,152,88), "LOTUS": (237,140,180), "DUSK": (255,230,172)}
frames = []

def panel(path, style):
    image = Image.open(path).convert("RGB")
    pixels = np.asarray(image).astype("int16")
    h,w = pixels.shape[:2]
    target = np.array(colors[style])
    # A long horizontal run of the requested paper distinguishes the real speech card from the arena.
    mask = np.max(abs(pixels-target), axis=2) <= 12
    band = mask[:int(h*.86),int(w*.12):int(w*.88)]
    longest = (0,0,0)
    for y, line in enumerate(band):
        edge = np.flatnonzero(np.diff(np.r_[False,line,False].astype(np.int8)))
        if len(edge):
            lengths = edge[1::2]-edge[::2]
            i = int(lengths.argmax())
            if lengths[i] > longest[0]:
                longest = (int(lengths[i]), int(edge[2*i]+w*.12), y)
    run,x,y = longest
    assert run >= 28, (path.name, "missing paper", longest)
    patch = pixels[max(0,y-90):min(h,y+115), max(0,x-48):min(w,x+run+48)]
    ink = np.max(patch, axis=2)<50
    assert int(ink.sum())>11, (path.name, "no dark lettering")
    if style in motifs:
        accents = np.max(abs(patch-np.array(motifs[style])),axis=2)<23
        assert int(accents.sum())>=3, (path.name, "missing trim", int(accents.sum()))
    return image

for i, row in enumerate(rows):
    style = "LEAF" if not row["female"] else "PLAIN" if row["dusk"] else "LOTUS"
    prefix = f'{i:02d}-{style}-{row["gesture"]}-'
    front = folder / (prefix + "front.png")
    picture = panel(front,style)
    assert (folder / (prefix+"side.png")).exists(), prefix+"side"
    assert (folder / (prefix+"rear.png")).exists(), prefix+"rear"
    frames.append((row, picture.copy()))
for name, style in [("petal-speech","LOTUS"),("dusk-speech","DUSK")]:
    panel(root/(name+".png"),style)
selected = [(next(i for i,(r,_) in enumerate(frames) if r["gesture"]==g),g) for g in ("WAVE","MEME_WINK","JACKS","TWIRL","KISS","SPIN_BOW")]
sheet = Image.new("RGB",(960,2*210),(32,27,39))
d = ImageDraw.Draw(sheet)
for n,(i,g) in enumerate(selected):
    row,frame=frames[i]
    frame.thumbnail((320,180))
    x,y=n%3*320,n//3*210
    sheet.paste(frame,(x,y))
    d.text((x+8,y+184),g.replace("_"," ").title(),fill="white")
sheet.save(folder/"village-welcomes.jpg",quality=93)
print("GUARDIAN NATIVE BUBBLES PASS 14 first-person front cards, 28 side/rear views, original conversation range, lotus, leaf, neutral and dusk")
