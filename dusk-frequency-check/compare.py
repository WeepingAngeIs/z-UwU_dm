#!/usr/bin/env python3
"""Aggregate hosted native survey output; this script does not simulate worldgen."""
from hashlib import sha256
import json
import os
from pathlib import Path

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Compare native survey evidence only in the authorized hosted workflow")

root = Path("build/dusk-frequency-evidence")
seeds = (152045, 20261121, 8675309)
expected_budget = 1024
baseline_jar_sha = "0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f"
keys = ("latticeCells", "usableGroves", "assignedDusk", "viableOriginal32", "viableExpanded", "accepted75",
        "petalPlans", "velvetOriginal32", "velvetExpanded", "velvetAccepted75")
totals = {key: 0 for key in keys}
selection = None
seed_rows = []
baseline_evidence = []
inputs = []
(root / "selected-seed.txt").unlink(missing_ok=True)

# Record every completed candidate's counters before applying improvement gates.
# A rejected candidate remains reviewable and never receives a PASS marker.
for seed in seeds:
    baseline_path = root / f"survey-baseline-{seed}.json"
    old = json.loads(baseline_path.read_text())
    new = json.loads((root / f"survey-candidate-{seed}.json").read_text())
    inputs.append((seed, old, new))
    for key in totals:
        totals[key] += new[key]
    baseline_evidence.append({
        "seed": seed, "path": str(baseline_path), "sha256": sha256(baseline_path.read_bytes()).hexdigest(),
        "originalSourceSha": old["sourceSha"], "loadedUwuJarSha256": old["loadedUwuJarSha256"],
        "preservedWithoutRelabeling": True,
    })
    recovered = [r for r in new["cells"] if r["expanded"] and not r["original32"] and r["accepted75"]
                 and r["plazaBiome"] == "uwudimension:velvet_highlands"]
    high_roll = [r for r in new["cells"] if r["expanded"] and r["roll"] >= 75]
    if selection is None and recovered and len(high_roll) >= 2 and new["petalPlans"] and new["viableOriginal32"]:
        selection = seed
    row = {k: new[k] for k in ("seed", "surveyAttemptBudget", *keys)}
    row.update({k: new[k] for k in ("sampleCallWallMillis", "maxCellCallWallMillis", "ordinaryServerTicksBetweenCells")})
    seed_rows.append(row)
    print("DUSK FREQUENCY SEED COUNTS", json.dumps(row, sort_keys=True), flush=True)

report = {
    "sourceSha": os.environ["GITHUB_SHA"],
    "loadedUwuJarSha256": os.environ["UWU_EXPECTED_JAR_SHA256"],
    "fixedSeeds": list(seeds),
    "surveyAttemptBudget": expected_budget,
    "selectionRule": "21 by21 lattice cells centered on each unchanged first-grove cell; no target-biome prefilter",
    "comparison": "Baseline100%/32 attempts versus candidate75%/1024 attempts; pure native terrain plans",
    "totals": totals,
    "bySeed": seed_rows,
    "baselineEvidence": baseline_evidence,
    "selectedNativeLifecycleSeed": selection,
    "nativePlacementCoverage": "Separate fresh/restart/legacy reports; these preview counts are not generated-village counts",
    "comparisonPassed": False,
}

def save_report():
    (root / "survey-comparison.json").write_text(json.dumps(report, indent=2) + "\n")

save_report()
print("DUSK FREQUENCY COMPARISON COUNTS", json.dumps(totals, sort_keys=True), flush=True)

try:
    fixture_hashes = set()
    for seed, old, new in inputs:
        assert old["stage"] == "survey-baseline" and old["loadedUwuJarSha256"] == baseline_jar_sha
        assert new["stage"] == "survey-candidate" and new["sourceSha"] == os.environ["GITHUB_SHA"]
        assert new["loadedUwuJarSha256"] == os.environ["UWU_EXPECTED_JAR_SHA256"]
        assert new["surveyAttemptBudget"] == expected_budget
        fixture_hashes.add(new["loadedFixtureJarSha256"])
        for key in ("seed", "originGx", "originGz", "windowRadiusInLatticeCells", "latticeCells",
                    "minimumSpacingBlocks", "latticeSpacingBlocks", "usableGroves", "assignedDusk",
                    "viableOriginal32", "petalPlans", "velvetOriginal32"):
            assert old[key] == new[key], (seed, key, old[key], new[key])
        assert old["previewGeneratedChunks"] == new["previewGeneratedChunks"] == 0
        assert new["latticeCells"] == 441 and new["minimumSpacingBlocks"] == 384 and new["latticeSpacingBlocks"] == 1536
        assert len(old["cells"]) == len(new["cells"])
        for before, after in zip(old["cells"], new["cells"]):
            for key in ("gx", "gz", "x", "z", "turns", "usable", "assignedDusk", "petal", "original32"):
                assert before[key] == after[key], (seed, key, before, after)
            if after["original32"]:
                assert after["expanded"] == after["original32"], (seed, "earliest successful32 plan changed")
            if after["expanded"]:
                assert after["accepted75"] == (after["roll"] < 75)
    assert len(fixture_hashes) == 1, "Candidate seed surveys used different fixture JARs"
    if os.environ.get("UWU_EXPECTED_PROBE_SHA256"):
        assert fixture_hashes == {os.environ["UWU_EXPECTED_PROBE_SHA256"]}
    report["loadedFixtureJarSha256"] = next(iter(fixture_hashes))
    report["petalPlansAndOriginal32SignaturesEqual"] = True
    report["zero75HundredSetsNested"] = True
    assert totals["viableExpanded"] > totals["viableOriginal32"], totals
    assert totals["accepted75"] > totals["viableOriginal32"], (
        "Expanded75 default failed to increase the fixed-sample overall count over baseline100/32", totals)
    assert totals["velvetAccepted75"] > totals["velvetOriginal32"], (
        "Expanded75 default failed to increase the fixed-sample Velvet Highlands count over baseline100/32", totals)
    assert selection is not None, "No fixed seed supports required native recovered Velvet + two high-roll + baseline/Petal controls"
except AssertionError as failure:
    report["failure"] = str(failure)
    save_report()
    raise

report["comparisonPassed"] = True
save_report()
(root / "selected-seed.txt").write_text(str(selection) + "\n")
print("DUSK FREQUENCY COMPARISON PASS", json.dumps(totals, sort_keys=True), "nativeSeed=" + str(selection))
