"""Match independently rendered frames from the same marked attack on the host server clock."""
import bisect
import csv
import json
import shutil
from pathlib import Path

evidence = Path("build/weapon-check/evidence")
output = evidence.parent / "paired"
output.mkdir(exist_ok=True)
clock = [list(map(int, row.split())) for row in (evidence/"host/server-clock.tsv").read_text().splitlines()]
times = [row[0] for row in clock]
columns = "item token actor viewer file phase game_tick animation_tick time clip active progress partial".split()

def samples(role):
    rows = list(csv.DictReader((evidence/role/"samples.tsv").open(), fieldnames=columns, delimiter="\t"))
    for row in rows:
        t = int(row["time"])
        slot = bisect.bisect_right(times, t)-1
        assert slot >= 0
        row["server_tick"] = clock[slot][1]
    return rows

host, observer = samples("host"), samples("observer")
items = sorted({r["item"] for r in host})
assert len(items) == 20
report, missing = [], []
for item in items:
    local = [r for r in host if r["item"] == item]
    remote = [r for r in observer if r["item"] == item]
    pairs = []
    for h in local:
        if h["phase"] != "swing" or h["active"] != "true":
            continue
        for o in remote:
            if (o["phase"] != "swing" or o["active"] != "true" or o["token"] != h["token"]
                or o["actor"] != h["actor"] or o["viewer"] == h["viewer"] or o["clip"] != h["clip"]
                or o["server_tick"] != h["server_tick"]):
                continue
            delta = abs(float(h["animation_tick"])-float(o["animation_tick"]))
            if delta > 1 or (item.endswith("_sai") and abs(float(h["progress"])-float(o["progress"]))>.19):
                continue
            pairs.append((h,o))
    chosen = []
    for lo, hi in [(0,.34),(.34,.67),(.67,1.01)]:
        band = [p for p in pairs if lo <= float(p[0]["progress"]) < hi]
        if band:
            chosen.append(min(band,key=lambda p:abs(float(p[0]["progress"])-(lo+hi)/2)))
    if not chosen: missing.append(item)
    selected=[(role,r) for h,o in chosen for role,r in [('host',h),('observer',o)]]
    for role,rows in [('host',local),('observer',remote)]:
        selected.extend((role,r) for r in rows if r["phase"] in ("rest","recovered"))
    for role,r in selected:
        folder=output/item/role
        folder.mkdir(exist_ok=True,parents=True)
        shutil.copy2(evidence/role/(r["file"]+".png"),folder)
    report.append(dict(item=item,pairs=chosen,exact_server_tick_matches=len(pairs)))
(output/"pairs.json").write_text(json.dumps(report,indent=2))
print("Distinct-player simultaneous swing pairs",sum(len(r["pairs"]) for r in report),"across",len(items)-len(missing),"items. Missing:",missing)
if missing:raise SystemExit(1)
