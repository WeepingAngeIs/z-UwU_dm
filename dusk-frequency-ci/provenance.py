"""Collect complete current evidence and explicitly attributed earlier baseline controls."""
from hashlib import sha256
import json
import os
from pathlib import Path
import struct
import subprocess


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


require(os.environ.get("GITHUB_ACTIONS") == "true"
        and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
        and os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm",
        "Run Dusk provenance only in the public repository on GitHub-hosted Actions")
require(json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())["repository"]["private"] is False,
        "Public repository evidence required")

folder = Path("build/dusk-frequency-ci")
evidence = Path("build/dusk-frequency-evidence")
scope = json.loads((folder / "scope.json").read_text())
require(scope["source_sha"] == os.environ["GITHUB_SHA"], "Scope is not for the current source")
candidate = Path(os.environ["UWU_JAR"])
require(digest(candidate) == scope["candidate_jar_sha256"], "The validated candidate changed before delivery")
inputs_path = folder / "inputs.json"
inputs = json.loads(inputs_path.read_text())
require(inputs["sourceSha"] == scope["source_sha"]
        and inputs["workflowRunId"] == os.environ["GITHUB_RUN_ID"], "Frozen inputs belong to another source/run")
probe = inputs["artifacts"]["probe"]
require(digest(Path(probe["path"])) == probe["sha256"]
        and probe["sha256"] == os.environ["UWU_EXPECTED_PROBE_SHA256"], "Frozen native fixture identity changed")

seeds = (152045, 20261121, 8675309)
attempts = 1024
baseline_source = "1f489c5308b686a30672ec19b671a64534eb28a4"
baseline_jar = "0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f"
earlier_source = "cc01a6c942353cbc570b786d9718a58c1438612a"
earlier_run = 44961077711
earlier_artifact = 14731811379
earlier_zip = "11d27a020204a20f617ccffeeb5208369070f22658b4942f91200e45cc5da934"
baseline_files = {
    "build/dusk-frequency-evidence/survey-baseline-152045.json": "bf0d530bf19cacf1b67ba04029ec206209bd71a383074ae9cb4ae46be483818f",
    "build/dusk-frequency-evidence/survey-baseline-20261121.json": "fe9654f12bc32177a569f82bbc01f5cc228ff81032d3a797b165eff4aaca71de",
    "build/dusk-frequency-evidence/survey-baseline-8675309.json": "d7aa16050aa7c64ad555bff9fdb564b132e968cd6229ff55f4a61e91a4a8c07f",
    "build/dusk-frequency-check/survey-baseline-152045.log": "ab92185ca25d96561404fbc9fc400e8fc6189eea5dae4fe1d8e7121d4496fea1",
    "build/dusk-frequency-check/survey-baseline-20261121.log": "031bc0b11c0bc7f51902612c2e01905d904b2e137a2aabf9799c9c585d62d734",
    "build/dusk-frequency-check/survey-baseline-8675309.log": "9edfd7da177e234590741237cc13b20455e5f7d6c1b8befc3944b439fc2a0c6a",
}
require(scope["baseline_source_sha"] == baseline_source and scope["baseline_jar_sha256"] == baseline_jar,
        "Unexpected integrated baseline generator identity")
recovery_path = folder / "baseline-survey-provenance.json"
recovery = json.loads(recovery_path.read_text())
require(recovery["schema_version"] == 1 and recovery["kind"] == "reused_completed_baseline_controls",
        "Missing explicit baseline-control recovery record")
require(recovery["source_workflow_run_id"] == earlier_run
        and recovery["source_workflow_run_status"] == "completed"
        and recovery["source_workflow_run_conclusion"] == "failure"
        and recovery["source_fixture_sha"] == earlier_source
        and recovery["baseline_generator_source_sha"] == baseline_source
        and recovery["baseline_jar_sha256"] == baseline_jar,
        "Earlier baseline controls were relabeled or their identity changed")
require(recovery["artifact"]["id"] == earlier_artifact and recovery["artifact"]["zip_sha256"] == earlier_zip,
        "Wrong baseline-control artifact")
require(recovery["fixed_seeds"] == list(seeds) and recovery["lattice_cells_per_seed"] == 441
        and recovery["candidate_evidence_reused"] is False and recovery["source_run_pass_claimed"] is False,
        "Recovery must be limited to the three completed baseline controls")
require(str(recovery["recovered_in_run"]) == os.environ["GITHUB_RUN_ID"]
        and recovery["recovered_for_source"] == scope["source_sha"], "Recovery was not verified for this run/source")
entries = recovery["files"]
require(len(entries) == len(baseline_files) and {row["path"] for row in entries} == set(baseline_files),
        "Baseline recovery contains missing, duplicated or extra files")
for row in entries:
    path = Path(row["path"])
    require(row["archive_path"] == path.relative_to("build").as_posix()
            and row["sha256"] == baseline_files[str(path)] == digest(path)
            and row["bytes"] == path.stat().st_size, "Recovered baseline bytes changed: " + str(path))

required_logs = {
    Path("build/dusk-frequency-check/server-pass.log"): "DUSK FREQUENCY SERVER PASS",
    Path("build/dusk-frequency-check/comparison.log"): "DUSK FREQUENCY COMPARISON PASS",
    Path("build/dusk-frequency-client.log"): "DUSK FREQUENCY UI PASS",
}
for seed in seeds:
    required_logs[Path(f"build/dusk-frequency-check/survey-candidate-{seed}.log")] = (
        f"DUSK FREQUENCY SURVEY PASS stage=survey-candidate seed={seed} cells=441 ")
    required_logs[Path(f"build/dusk-frequency-check/survey-pass-{seed}.log")] = (
        f"DUSK FREQUENCY SURVEY JOB PASS seed={seed}")
for path, marker in required_logs.items():
    log = path.read_text(errors="replace")
    require(marker in log and "CHECK FAILED" not in log and "OutOfMemoryError" not in log,
            "Incomplete hosted evidence: " + str(path))

baseline_names = {f"survey-baseline-{seed}.json" for seed in seeds}
required_reports = ["survey-comparison.json", "fresh.json", "restart.json", "legacy-seed.json",
                    "legacy-upgrade.json", "legacy-restart.json", "ui.json"]
for seed in seeds:
    required_reports += [f"survey-baseline-{seed}.json", f"survey-candidate-{seed}.json",
                         f"native-sample-{seed}.json"]
reports, data_by_name = {}, {}
for name in required_reports:
    path = evidence / name
    data = json.loads(path.read_text())
    reused = name in baseline_names
    expected_source = earlier_source if reused else scope["source_sha"]
    expected_jar = baseline_jar if reused or name == "legacy-seed.json" else scope["candidate_jar_sha256"]
    require(data.get("sourceSha") == expected_source, "Wrong or missing fixture source identity: " + name)
    require(data.get("loadedUwuJarSha256") == expected_jar, "Wrong or missing loaded JAR identity: " + name)
    if not reused and name != "ui.json":
        require(data.get("loadedFixtureJarSha256") == probe["sha256"], "Wrong or missing loaded native fixture: " + name)
    elif name == "ui.json" and "loadedFixtureJarSha256" in data:
        require(data["loadedFixtureJarSha256"] == probe["sha256"], "Unexpected UI fixture identity")
    data_by_name[name] = data
    reports[name] = {"path": str(path), "sha256": digest(path), "fixture_source_sha": expected_source,
                     "loaded_uwu_jar_sha256": expected_jar, "reused_earlier_baseline_control": reused,
                     "loaded_native_fixture_jar_sha256": data.get("loadedFixtureJarSha256")}

comparison = data_by_name["survey-comparison.json"]
require(comparison["fixedSeeds"] == list(seeds) and comparison["surveyAttemptBudget"] == attempts,
        "The predeclared seeds or expanded attempt budget changed")
require(comparison["comparisonPassed"] is True and comparison["petalPlansAndOriginal32SignaturesEqual"] is True
        and comparison["zero75HundredSetsNested"] is True, "Missing plan/percentage invariants")
require(len(comparison["baselineEvidence"]) == len(seeds), "Incomplete original-control attribution")
for seed, row in zip(seeds, comparison["baselineEvidence"]):
    path = f"build/dusk-frequency-evidence/survey-baseline-{seed}.json"
    require(row["seed"] == seed and row["path"] == path and row["sha256"] == baseline_files[path]
            and row["originalSourceSha"] == earlier_source and row["loadedUwuJarSha256"] == baseline_jar
            and row["preservedWithoutRelabeling"] is True, "Comparison relabeled an earlier baseline control")
totals = comparison["totals"]
require(totals["viableExpanded"] > totals["viableOriginal32"]
        and totals["accepted75"] > totals["viableOriginal32"],
        "The default did not improve overall fixed-sample frequency")
require(totals["velvetAccepted75"] > totals["velvetOriginal32"],
        "The default did not improve Velvet Highlands fixed-sample frequency")
sum_keys = ("latticeCells", "usableGroves", "assignedDusk", "viableOriginal32", "viableExpanded", "accepted75",
            "petalPlans", "velvetOriginal32", "velvetExpanded", "velvetAccepted75")
require([row["seed"] for row in comparison["bySeed"]] == list(seeds), "Wrong per-seed comparison rows")
for seed, row in zip(seeds, comparison["bySeed"]):
    old = data_by_name[f"survey-baseline-{seed}.json"]
    new = data_by_name[f"survey-candidate-{seed}.json"]
    require(old["seed"] == new["seed"] == seed and old["stage"] == "survey-baseline"
            and new["stage"] == "survey-candidate", f"Wrong survey stage/seed {seed}")
    require(new["surveyAttemptBudget"] == attempts and len(old["cells"]) == len(new["cells"]) == 441
            and old["latticeCells"] == new["latticeCells"] == 441
            and old["previewGeneratedChunks"] == new["previewGeneratedChunks"] == 0,
            f"Incomplete native terrain survey {seed}")
    for key in ("originGx", "originGz", "windowRadiusInLatticeCells", "minimumSpacingBlocks", "latticeSpacingBlocks",
                "usableGroves", "assignedDusk", "viableOriginal32", "petalPlans", "velvetOriginal32"):
        require(old[key] == new[key], f"Changed baseline survey invariant {seed}/{key}")
    for before, after in zip(old["cells"], new["cells"]):
        for key in ("gx", "gz", "x", "z", "turns", "usable", "assignedDusk", "petal", "original32"):
            require(before[key] == after[key], f"Changed baseline cell invariant {seed}/{key}")
        if after["original32"]:
            require(after["expanded"] == after["original32"], f"Changed first32 plan {seed}")
        if after["expanded"]:
            require(0 <= after["roll"] < 100 and after["accepted75"] == (after["roll"] < 75),
                    f"Incorrect deterministic75 roll {seed}")
        else:
            require(after["accepted75"] is False, f"Accepted uninhabitable site {seed}")
    require(all(row[key] == new[key] for key in sum_keys), f"Summary differs from candidate report {seed}")
    log = Path(f"build/dusk-frequency-check/survey-baseline-{seed}.log").read_text()
    require(f"DUSK FREQUENCY SURVEY PASS stage=survey-baseline seed={seed} cells=441 " in log
            and f"UWU PACKAGED CANDIDATE PASS sha256={baseline_jar} " in log,
            f"Missing earlier completed baseline-control markers {seed}")
require(all(totals[key] == sum(row[key] for row in comparison["bySeed"]) for key in sum_keys),
        "Aggregate counts differ from the three fixed candidate samples")
selected = comparison["selectedNativeLifecycleSeed"]
require(selected in seeds, "Selected lifecycle seed is outside the fixed sample")

for name in ("fresh.json", f"native-sample-{selected}.json"):
    data = data_by_name[name]
    require(data["seed"] == selected and data["stage"] == "fresh"
            and data["nativeDuskVillages"] == 2 and data["nativePetalControls"] == 1
            and data["expandedOnlyNativeBiome"] == "uwudimension:velvet_highlands"
            and data["preservedAcrossLiveEdit"] is True and data["zeroBlankFootprintTouched"] is True
            and data["duskCupidShrines"] == 0, "Missing actual native placement controls: " + name)
    require(all(data.get(key) for key in ("expandedOnlyAccepted75", "frozenRejected0", "untouchedThenAccepted100")),
            "Missing accepted/rejected site identity: " + name)
require(data_by_name["fresh.json"] == data_by_name[f"native-sample-{selected}.json"],
        "Selected seed's native placement report differs from its lifecycle report")
for seed in seeds:
    if seed == selected:
        continue
    name = f"native-sample-{seed}.json"
    data = data_by_name[name]
    require(data["seed"] == seed and data["stage"] == "native-sample" and data["nativeDuskVillages"] == 1
            and data["nativeDuskResidents"] > 0 and data["duskCupidShrines"] == 0
            and data.get("plan") and data.get("nativeBiome"), "Missing actual native placement on fixed seed: " + name)
restart = data_by_name["restart.json"]
require(restart["seed"] == selected and restart["acceptedAndRejectedPolicySurvivedRestart"] is True
        and restart["structureAndResidentSnapshotsEqual"] is True, "Incomplete current-world restart evidence")
legacy_seed = data_by_name["legacy-seed.json"]
require(legacy_seed["seed"] == selected and legacy_seed["partialDuskAndPetal"] is True
        and legacy_seed["preExistingBlankFootprint"] is True, "Incomplete current-fixture historical controls")
for name in ("legacy-upgrade.json", "legacy-restart.json"):
    data = data_by_name[name]
    require(data["seed"] == selected and data["partialPlansCompletedWithoutMoving"] is True
            and data["savedBlocksAndResidentsEqual"] is True and data["oldEmptyFootprintRemainsEmpty"] is True
            and data["historicalDuskBudget"] == 32 and data["historicalDuskChance"] == 100,
            "Incomplete historical protection evidence: " + name)

companion_path = folder / "companion/companion-provenance.json"
companion = json.loads(companion_path.read_text())
companion_jar = Path(os.environ["UWU_COMPANION_JAR"])
require(digest(companion_jar) == companion["artifact"]["sha256"], "Companion changed after its recorded build")
require(companion["uwu_commit"] == scope["source_sha"]
        and companion["upstream_commit"] == "0b01111213c6d6aaf63a955a5c010bc1a63ee04c"
        and companion["neoforge_build_override"] == "21.1.249", "Incorrect companion source/runtime identity")
patch = Path("dusk-frequency-ci/companion/Server-Jars-config.patch")
require(digest(patch) == companion["coordination_patch_sha256"], "The tested companion patch differs from the handoff")

ui = data_by_name["ui.json"]
require(ui["candidateSha256"] == scope["candidate_jar_sha256"]
        and ui["companionJarSha256"] == companion["artifact"]["sha256"]
        and ui["seed"] == selected and ui["nativePositivePath"] is True
        and ui["positiveEditsUseDirectHandler"] is False, "Wrong native menu path or runtime identity")
require([row["percentage"] for row in ui["edits"]] == [0, 75, 100], "Missing native0/75/100 menu sequence")
for row in ui["edits"]:
    value = row["percentage"]
    require(row["serverGetter"] == row["frozenPercentage"] == value and row["tomlSaved"] is True
            and row["nativeVillageGenerated"] == (value != 0), f"Menu edit did not reach persistence/generation: {value}")
    require(row["nativeResidents"] > 0 if value else row["nativeResidents"] == 0,
            f"Wrong native residents for menu edit {value}")
    if value == 75:
        require(row["nativeBiome"] == "uwudimension:velvet_highlands", "GUI75 did not generate a Velvet village")
guard_labels = {"non_operator", "stale_expected_value", "unknown_key", "below_minimum", "above_maximum",
                "fractional_number", "boolean_value", "null_value", "array_value", "quoted_number"}
require(len(ui["guardChecks"]) == len(guard_labels)
        and {row["label"] for row in ui["guardChecks"]} == guard_labels, "Incomplete negative handler controls")
require(all(row["wireRoundTrip"] is True and row["getterUnchanged"] is True and row["tomlByteIdentical"] is True
            and row["currentPercent"] == 100 for row in ui["guardChecks"]), "A negative handler control failed")

captures = sorted(evidence.rglob("ui-*.png"))
required_captures = {f"ui-{state}-{value}.png" for state in ("edited", "saved") for value in (0, 75, 100)}
require(len(captures) == 6 and {p.name for p in captures} == required_captures,
        "Missing or duplicated native editor/saved-value captures")
require(set(ui["captures"]) == {name.removesuffix(".png") for name in required_captures}, "Capture metadata differs")
for path in captures:
    raw = path.read_bytes()
    require(raw[:8] == b"\x89PNG\r\n\x1a\n" and raw[12:16] == b"IHDR", "Invalid native PNG: " + str(path))
    width, height = struct.unpack(">II", raw[16:24])
    frame = ui["captures"][path.stem]
    expected_screen = "dev.unyielding.quests.client.ConfigValuesScreen" + ("$ValueScreen" if "-edited-" in path.name else "")
    require(width >= 800 and height >= 450 and (frame["width"], frame["height"]) == (width, height)
            and frame["file"] == path.name and frame["screen"] == expected_screen, "Wrong native menu capture: " + str(path))

log_paths = sorted(set(required_logs) | set(Path("build/dusk-frequency-check").glob("*.log")))
markers = []
for path in log_paths:
    log = path.read_text(errors="replace")
    require("CHECK FAILED" not in log and "OutOfMemoryError" not in log, "Failure in focused evidence: " + str(path))
    markers += [{"path": str(path), "reused_earlier_baseline_control": str(path) in baseline_files, "text": line}
                for line in log.splitlines() if " PASS" in line]

report = {
    **scope,
    "workflow_run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "environment": "Public GitHub-hosted ubuntu-24.04, Java 21, NeoForge 21.1.249",
    "frozen_inputs": {"path": str(inputs_path), "sha256": digest(inputs_path),
                      "native_fixture_jar_sha256": probe["sha256"]},
    "setting": {"file": "uwudimension-server.toml", "key": "duskVillageChancePercent", "default": 75,
                "range_inclusive": [0, 100], "denominator": "Habitable new Dusk lattice sites in Sakura Reach",
                "existing_and_generation_touched_sites_keep_their_policy": True},
    "earlier_completed_baseline_controls": {"record_path": str(recovery_path), "record_sha256": digest(recovery_path), **recovery},
    "native_survey_comparison": comparison,
    "native_placement_seeds": list(seeds),
    "focused_reports": reports,
    "menu_captures": [{"path": str(p), "sha256": digest(p)} for p in captures],
    "markers": markers,
    "companion": companion,
    "external_repositories_modified_or_published": False,
    "limitations": [
        "Only three completed baseline-control surveys and individual passing logs were reused from run44961077711/sourcecc01a6c. That earlier run failed its aggregate candidate-density check and is not claimed as passing.",
        "Every candidate survey, native placement, persistence and GUI report belongs to this current source and packaged candidate; the newly generated legacy-seed control uses the original baseline JAR with the current fixture.",
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
    summary.write(f"Source: {scope['source_sha']}\n\nBaseline generator: {baseline_source}\n\n")
    summary.write(f"UwU JAR SHA-256: {scope['candidate_jar_sha256']}\n\n")
    summary.write(f"Three completed baseline controls were reused unchanged from [run {earlier_run}](https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/{earlier_run}), fixture {earlier_source}. That earlier aggregate run failed; its candidate results are not reused.\n\n")
    summary.write("### Fixed native terrain survey\n\n")
    summary.write("| Seed | Lattice cells | Usable groves | Assigned Dusk | Baseline32/100% | Expanded1024/100% | Expanded1024/75% | Velvet baseline | Velvet default |\n")
    summary.write("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n")
    for row in comparison["bySeed"]:
        summary.write("| " + " | ".join(str(row[k]) for k in ("seed", "latticeCells", "usableGroves", "assignedDusk",
                      "viableOriginal32", "viableExpanded", "accepted75", "velvetOriginal32", "velvetAccepted75")) + " |\n")
    summary.write("\nBoth overall and Velvet Highlands default counts strictly exceed their baseline counts. Native placement, restart, historical protection and actual menu evidence are separate current reports.\n\n")
    summary.write("The companion patch is reviewable and tested here; no external repository or combined release was changed.\n")
print("DUSK FREQUENCY PROVENANCE PASS")
