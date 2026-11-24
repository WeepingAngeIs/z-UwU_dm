"""Reuse pinned native captures and replace only the two Nether controls."""
from pathlib import Path
import shutil
import sys
import numpy as np
from PIL import Image

root = Path(sys.argv[1])
new = root / "evidence"
old = root / "previous/evidence"
dest = root / "combined"
shutil.copytree(old, dest)
rows = {row.split("\t")[0]:row for row in (new/"views.tsv").read_text().splitlines()}
assert set(rows) == {"the_nether-0", "the_nether-2", "the_nether-off-repeat"}
def capture(name):
    return np.asarray(Image.open(new/(name+".png")).convert("RGB"),dtype=np.int16)
base = capture("the_nether-0")
assert base[-80:,-400:].std(axis=(0,1)).max()>2, "Off control lacks textured terrain"
assert (abs(base-capture("the_nether-off-repeat")).max(axis=2)>8).mean()<.015
for name in ["the_nether-0","the_nether-2"]:
    shutil.copyfile(new/(name+".png"),dest/(name+".png"))
original = (old/"views.tsv").read_text().splitlines()
(dest/"views.tsv").write_text("\n".join(rows.get(line.split("\t")[0],line) for line in original)+"\n")
