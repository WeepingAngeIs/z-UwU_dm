"""Collect only complete hosted native frequency, preservation and menu evidence."""
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Run Dusk provenance only on GitHub-hosted Actions")

folder = Path("build/dusk-frequency-ci")
evidence = Path("build/dusk-frequency-evidence")
scope = json.loads((folder / "scope.json").read_text())
candidate = Path(os.environ["UWU_JAR"])
if sha256(candidate.read_bytes()).hexdigest() != scope["candidate_jar_sha256"]:
    raise AssertionError("The validated candidate changed before delivery")

required_logs = {
    Path("build/dusk-frequency-check/server-pass.log"): "DUSK FREQUENCY SERVER PASS",
    Path("build/dusk-frequency-check/comparison.log"): "DUSK FREQUENCY COMPARISON PASS",
    Path("build/dusk-frequency-client.log"): "DUSK FREQUENCY UI PASS",
}
for path, marker in required_logs.items():
    log = path.read_text(errors="replace")
    if marker not in log or "CHECK FAILED" in log or "OutOfMemoryError" in log:
        raise AssertionError("Incomplete hosted evidence: " + str(path))

seeds = (152045, 20261121, 8675309)
required_reports = ["survey-comparison.json", "fresh.json", "restart.json", "legacy-seed.json",
                    "legacy-upgrade.json", "legacy-restart.json", "ui.json"]
for seed in seeds:
    required_reports += [f"survey-baseline-{seed}.json", f"survey-candidate-{seed}.json",
                         f"native-sample-{seed}.json"]
reports = {}
for name in required_reports:
    path = evidence / name
    data = json.loads(path.read_text())
    if "sourceSha" in data and data["sourceSha"] != scope["source_sha"]:
        raise AssertionError("Wrong fixture source identity: " + name)
    if "loadedUwuJarSha256" in data:
        baseline = name.startswith("survey-baseline-") or name == "legacy-seed.json"
        expected = scope["baseline_jar_sha256"] if baseline else scope["candidate_jar_sha256"]
        if data["loadedUwuJarSha256"] != expected:
            raise AssertionError("Wrong loaded JAR in " + name)
    reports[name] = {"path": str(path), "sha256": sha256(path.read_bytes()).hexdigest()}

comparison = json.loads((evidence / "survey-comparison.json").read_text())
if comparison["fixedSeeds"] != list(seeds):
    raise AssertionError("The predeclared seed sample changed")
totals = comparison["totals"]
if totals["accepted75"] <= totals["viableOriginal32"]:
    raise AssertionError("The default did not improve measured eligible-site frequency")

captures = sorted(evidence.rglob("ui-*.png"))
required_captures = {f"ui-{state}-{value}.png" for state in ("edited", "saved") for value in (0, 75, 100)}
if not required_captures <= {p.name for p in captures}:
    raise AssertionError("Missing native editor or saved-value captures")

companion_path = folder / "companion/companion-provenance.json"
companion = json.loads(companion_path.read_text())
companion_jar = Path(os.environ["UWU_COMPANION_JAR"])
if sha256(companion_jar.read_bytes()).hexdigest() != companion["artifact"]["sha256"]:
    raise AssertionError("Companion changed after its recorded build")
if companion["uwu_commit"] != scope["source_sha"] or companion["upstream_commit"] != "0b01111213c6d6aaf63a955a5c010bc1a63ee04c":
    raise AssertionError("Incorrect companion source identity")
patch = Path("dusk-frequency-ci/companion/Server-Jars-config.patch")
if sha256(patch.read_bytes()).hexdigest() != companion["coordination_patch_sha256"]:
    raise AssertionError("The tested companion patch differs from the handoff")

log_paths = sorted(set(required_logs) | set(Path("build/dusk-frequency-check").glob("*.log")))
markers = []
for path in log_paths:
    log = path.read_text(errors="replace")
    if "CHECK FAILED" in log or "OutOfMemoryError" in log:
        raise AssertionError("Failure in focused evidence: " + str(path))
    markers += [line for line in log.splitlines() if " PASS" in line]

report = {
    **scope,
    "workflow_run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "environment": "Public GitHub-hosted ubuntu-24.04, Java 21, NeoForge 21.1.249",
    "setting": {"file": "uwudimension-server.toml", "key": "duskVillageChancePercent", "default": 75,
                "range_inclusive": [0, 100], "denominator": "Habitable new Dusk lattice sites in Sakura Reach",
                "existing_and_generation_touched_sites_keep_their_policy": True},
    "native_survey_comparison": comparison,
    "native_placement_seeds": list(seeds),
    "focused_reports": reports,
    "menu_captures": [{"path": str(p), "sha256": sha256(p.read_bytes()).hexdigest()} for p in captures],
    "markers": markers,
    "companion": companion,
    "external_repositories_modified_or_published": False,
    "limitations": [
        "Survey counts are deterministic native terrain plans, with bounded actual generation samples on each of three seeds.",
        "The percentage is per eligible new Dusk site; it is not the fraction of chunks or biome area containing villages.",
        "The GUI was tested with a separately built patched quests module, not an installed combined Unyielding profile.",
        "Companion integration into the external Unyielding distribution still requires its maintainer to apply the supplied patch.",
        "Only disposable hosted worlds were generated; no user world, profile or server was accessed.",
        "Independent dialogue and weapon branches are not included in this JAR.",
    ],
}
(folder / "provenance.json").write_text(json.dumps(report, indent=2) + "\n")
(folder / "dusk-frequency.patch").write_bytes(subprocess.check_output(
    ["git", "diff", "--binary", scope["baseline_source_sha"], "HEAD"]))
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write("## Dusk frequency and Unyielding setting\n\n")
    summary.write(f"Source: `{scope['source_sha']}`\n\nBaseline: `{scope['baseline_source_sha']}`\n\n")
    summary.write(f"UwU JAR SHA-256: `{scope['candidate_jar_sha256']}`\n\n")
    summary.write("### Fixed native terrain survey\n\n")
    summary.write("| Seed | Lattice cells | Usable groves | Assigned Dusk | Baseline 32/100% | Expanded 256/100% | Expanded 256/75% |\n")
    summary.write("|---|---:|---:|---:|---:|---:|---:|\n")
    for row in comparison["bySeed"]:
        summary.write("| " + " | ".join(str(row[k]) for k in ("seed", "latticeCells", "usableGroves", "assignedDusk",
                      "viableOriginal32", "viableExpanded256", "accepted75")) + " |\n")
    summary.write("\nNative placement, persistence, legacy protection and menu evidence are separate reports in the evidence artifact.\n\n")
    summary.write("The companion patch is reviewable and tested here; no external repository or combined release was changed.\n")
print("DUSK FREQUENCY PROVENANCE PASS")
