"""Seal one hosted build and verify its exact inputs in each following hosted job."""
from hashlib import sha256
import json
import os
from pathlib import Path
import sys

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Frozen inputs are for GitHub-hosted Actions only")
if os.environ.get("GITHUB_REPOSITORY") != "WeepingAngeIs/z-UwU_dm":
    raise SystemExit("Unexpected repository")
event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
if event.get("repository", {}).get("private") is not False:
    raise SystemExit("Only the public repository is permitted")

root = Path.cwd().resolve()
folder = Path("build/dusk-frequency-ci")
manifest = folder / "inputs.json"
seeds = (152045, 20261121, 8675309)
identities = {
    "candidate": "UWU_JAR",
    "baseline": "UWU_BASELINE_JAR",
    "probe": "UWU_PROBE_JAR",
    "companion": "UWU_COMPANION_JAR",
}

def key(path):
    result = Path(path).resolve().relative_to(root)
    if not result.is_relative_to("build") or not Path(path).is_file():
        raise AssertionError("Expected a regular hosted build input: " + str(path))
    return result.as_posix()

def digest(path):
    return sha256(Path(path).read_bytes()).hexdigest()

mode = sys.argv[1] if len(sys.argv) == 2 else ""
if mode == "seal":
    selected = [Path(os.environ[name]) for name in identities.values()]
    selected += [folder / name for name in ("scope.json", "candidate.sha256", "baseline-survey-provenance.json")]
    selected += [folder / "companion" / name for name in
                 ("companion-provenance.json", "SHA256SUMS", "companion-build.log")]
    for seed in seeds:
        selected += [Path(f"build/dusk-frequency-evidence/survey-baseline-{seed}.json"),
                     Path(f"build/dusk-frequency-check/survey-baseline-{seed}.log")]
    classes = sorted(Path("build/classes/java/main").rglob("*.class"))
    if not classes:
        raise AssertionError("The frozen build has no compiled main classes")
    selected += classes
    data = {
        "sourceSha": os.environ["GITHUB_SHA"],
        "workflowRunId": os.environ["GITHUB_RUN_ID"],
        "artifacts": {label: {"path": key(os.environ[name]), "sha256": digest(os.environ[name])}
                      for label, name in identities.items()},
        "files": {key(path): digest(path) for path in sorted(selected)},
        "meaning": "Build inputs only; successful native and UI checks are still required for the final test artifact.",
    }
    folder.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(data, indent=2) + "\n")
elif mode == "verify":
    data = json.loads(manifest.read_text())
else:
    raise SystemExit("Usage: inputs.py seal|verify")

if data["sourceSha"] != os.environ["GITHUB_SHA"] or data["workflowRunId"] != os.environ["GITHUB_RUN_ID"]:
    raise AssertionError("Frozen inputs came from a different source or workflow run")
for name, expected in data["files"].items():
    if key(name) != name or digest(name) != expected:
        raise AssertionError("Frozen input changed: " + name)
for label, env in identities.items():
    record = data["artifacts"][label]
    if key(os.environ[env]) != record["path"] or digest(record["path"]) != record["sha256"]:
        raise AssertionError("Wrong staged artifact: " + label)
if data["artifacts"]["baseline"]["sha256"] != "0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f":
    raise AssertionError("Wrong integrated Playtest 9 baseline")
with open(os.environ["GITHUB_ENV"], "a") as exported:
    for label, env in identities.items():
        exported.write(env + "=" + str((root / data["artifacts"][label]["path"]).resolve()) + "\n")
    exported.write("UWU_EXPECTED_JAR_SHA256=" + data["artifacts"]["candidate"]["sha256"] + "\n")
    exported.write("UWU_EXPECTED_PROBE_SHA256=" + data["artifacts"]["probe"]["sha256"] + "\n")
print("DUSK FROZEN INPUTS", mode.upper(), "PASS", data["sourceSha"],
      "candidate=" + data["artifacts"]["candidate"]["sha256"],
      "probe=" + data["artifacts"]["probe"]["sha256"])
