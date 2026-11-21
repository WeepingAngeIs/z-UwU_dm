#!/usr/bin/env python3
"""Aggregate hosted native survey output; this script does not simulate worldgen."""
import json
from pathlib import Path

root = Path("build/dusk-frequency-evidence")
seeds = (152045, 20261121, 8675309)
totals = {key: 0 for key in ("latticeCells", "usableGroves", "assignedDusk", "viableOriginal32", "viableExpanded256", "accepted75", "petalPlans", "velvetOriginal32", "velvetExpanded256", "velvetAccepted75")}
selection = None
seed_rows = []
for seed in seeds:
    old = json.loads((root / f"survey-baseline-{seed}.json").read_text())
    new = json.loads((root / f"survey-candidate-{seed}.json").read_text())
    for key in ("seed", "originGx", "originGz", "windowRadiusInLatticeCells", "latticeCells", "minimumSpacingBlocks", "latticeSpacingBlocks", "usableGroves", "assignedDusk", "viableOriginal32", "petalPlans"):
        assert old[key] == new[key], (seed, key, old[key], new[key])
    assert old["previewGeneratedChunks"] == new["previewGeneratedChunks"] == 0
    assert new["latticeCells"] == 441 and new["minimumSpacingBlocks"] == 384 and new["latticeSpacingBlocks"] == 1536
    assert len(old["cells"]) == len(new["cells"])
    for before, after in zip(old["cells"], new["cells"]):
        for key in ("gx", "gz", "x", "z", "turns", "usable", "assignedDusk", "petal", "original32"):
            assert before[key] == after[key], (seed, key, before, after)
        if after["original32"]:
            assert after["expanded256"] == after["original32"], (seed, "earliest successful32 plan changed")
        if after["expanded256"]:
            assert after["accepted75"] == (after["roll"] < 75)
    for key in totals:
        totals[key] += new[key]
    recovered = [r for r in new["cells"] if r["expanded256"] and not r["original32"] and r["accepted75"] and r["plazaBiome"] == "uwudimension:velvet_highlands"]
    high_roll = [r for r in new["cells"] if r["expanded256"] and r["roll"] >= 75]
    if selection is None and recovered and len(high_roll) >= 2 and new["petalPlans"] and new["viableOriginal32"]:
        selection = seed
    seed_rows.append({k: new[k] for k in ("seed", *totals.keys())})
assert totals["viableExpanded256"] > totals["viableOriginal32"], totals
assert totals["accepted75"] > totals["viableOriginal32"], ("Expanded75 default failed to increase the fixed-sample count over baseline100/32", totals)
assert selection is not None, "No fixed seed supports required native recovered Velvet + two high-roll + baseline/Petal controls"
report = {
    "fixedSeeds": list(seeds),
    "selectionRule": "21 by21 lattice cells centered on each unchanged first-grove cell; no target-biome prefilter",
    "comparison": "Baseline100%/32 attempts versus candidate75%/256 attempts; pure native terrain plans",
    "totals": totals,
    "bySeed": seed_rows,
    "selectedNativeLifecycleSeed": selection,
    "nativePlacementCoverage": "Separate fresh/restart/legacy reports; these preview counts are not generated-village counts",
    "petalPlansAndOriginal32SignaturesEqual": True,
    "zero75HundredSetsNested": True,
}
(root / "survey-comparison.json").write_text(json.dumps(report, indent=2) + "\n")
(root / "selected-seed.txt").write_text(str(selection) + "\n")
print("DUSK FREQUENCY COMPARISON PASS", json.dumps(totals, sort_keys=True), "nativeSeed=" + str(selection))
