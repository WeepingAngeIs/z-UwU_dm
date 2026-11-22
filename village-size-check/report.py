"""Summarize surveyed blocks and draw representative layouts from native server evidence."""
import json
import os
from pathlib import Path
import shutil
from PIL import Image, ImageDraw, ImageFont

inputs, evidence, out = map(Path, ["build/village-input", "build/village-evidence", "build/village-final"])
out.mkdir(exist_ok=True)
def read(name):
    return json.loads((evidence / (name + ".json")).read_text())
seeds = (152045, 20261121, 8675309)
rows = [v for seed in seeds for v in read("layouts-" + str(seed))]
assert len(rows) == 9 and {r["seed"] for r in rows} == set(seeds)
assert {r["actualHouses"] for r in rows} == set(range(6, 13))
for faction in ("Petal","Dusk"):
    assert {6,12} <= {r["actualHouses"] for r in rows if r["faction"] == faction}
assert all(r["actualHouses"] == len(r["houses"]) and r["walkablePathColumns"] > 0 and r["beds"] >= len(r["houses"]) for r in rows)
assert read("restart-152045") == read("layouts-152045")
partial, upgraded = read("oldpartial-152045"), read("upgrade-152045")
assert partial["oldHouseCount"] == upgraded["actualHouses"] and partial["unloadedHouseChunks"] > 0
scope = json.loads((inputs / "scope.json").read_text())
shutil.copyfile(inputs / "candidate.jar", out / scope["candidateName"])
(out / "SHA256SUMS.txt").write_text(scope["candidateSha256"] + "  " + scope["candidateName"] + "\n")
report = dict(source=os.environ["GITHUB_SHA"], run=os.environ["GITHUB_RUN_ID"], scope=scope,
              fresh=rows, partiallyGeneratedOnBaseline=partial, onUpgrade=upgraded, freshRestartStable=True)
(out / "village-checks.json").write_text(json.dumps(report, indent=2) + "\n")
print("NATIVE FRESH HOUSE COUNTS", [(r["seed"],r["faction"],r["actualHouses"]) for r in rows])
print("UPGRADE", upgraded["plaza"], upgraded["actualHouses"], "houses")
print("VERIFIED", scope["candidateSha256"], scope["candidateName"])

im = Image.new("RGB", (1104,932), "#fbf8f3")
draw = ImageDraw.Draw(im)
font_dir = Path("/usr/share/fonts/truetype/dejavu")
def font(size, weight=""):
    return ImageFont.truetype(str(font_dir / ("DejaVuSans" + weight + ".ttf")), size)
title, heading, small = font(31,"-Bold"), font(21,"-Bold"), font(15)
draw.text((32,19), "Sakura Reach village layouts", font=title, fill="#362b3b")
draw.text((33,65), "Plan view of homes, entrances and paths counted in generated Minecraft chunks.", font=small, fill="#594f57")
palettes = {"Petal":("#d77797","#8c425a","#fff4f0"),"Dusk":("#85699f","#543e68","#f3edf6")}
for i,(faction,count) in enumerate([("Petal",6),("Petal",12),("Dusk",6),("Dusk",12)]):
    row = next(r for r in rows if r["faction"] == faction and r["actualHouses"] == count)
    x, y = 31+(i%2)*532, 111+(i//2)*376
    roof,edge,paper = palettes[faction]
    draw.rounded_rectangle((x,y,x+510,y+353), 16, fill=paper,outline="#d5cbd0",width=2)
    draw.text((x+18,y+12), f"{faction}   ·   {count} houses",font=heading,fill=edge)
    draw.text((x+18,y+46), f"Seed {row['seed']}   ·   Plaza {row['plaza']}",font=small,fill="#615969")
    scale=3.0
    lo=min(h["z"]-h["depth"] for h in row["houses"])
    hi=max(h["z"]+h["depth"] for h in row["houses"])
    def pt(ex,ez):
        return (round(x+255+scale*ex), round(y+206+scale*(ez-(lo+hi)/2)))
    bends=row["bends"]
    draw.line([pt(dx-len(bends)//2,bend) for dx,bend in enumerate(bends)],fill="#ab9274",width=9)
    for h in row["houses"]:
        hx,hz,side=h["x"],h["z"],h["side"]
        dz=hz-side*h["depth"]
        draw.line((pt(hx,bends[hx+len(bends)//2]),pt(hx,dz)),fill="#b7a287",width=3)
        draw.rectangle((*pt(hx-h["width"],hz-h["depth"]),*pt(hx+h["width"],hz+h["depth"])),fill=roof,outline=edge,width=2)
        a,b=pt(hx,dz)
        draw.ellipse((a-2,b-2,a+2,b+2),fill="#fff5d8",outline="#543f35")
    a,b=pt(0,0)
    draw.polygon([(a,b-5),(a+5,b),(a,b+5),(a-5,b)],fill="#f6c56a",outline="#715a38")
draw.text((34,877),"Gold diamond: plaza     Pale dot: verified doorway     Tan line: paved approach or main street",font=small,fill="#594f57")
im.save(out/"village-layouts.png",optimize=True)
