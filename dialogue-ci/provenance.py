"""Collect the passing hosted dialogue checks and the exact downloadable patch."""
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Run dialogue provenance only on GitHub Actions")
folder = Path("build/dialogue-ci")
scope = json.loads((folder / "scope.json").read_text())
candidate = Path(os.environ["UWU_JAR"])
if sha256(candidate.read_bytes()).hexdigest() != scope["candidate_jar_sha256"]:
    raise AssertionError("The validated candidate changed before delivery")
evidence = {}
for mode in ("without-reference", "with-reference"):
    log = Path(f"build/dialogue-{mode}.log").read_text(errors="replace")
    for marker in ("UWU PACKAGED CANDIDATE PASS", "KEEPER SPEECH RECIPIENTS PASS",
                   "KEEPER SPEECH CLIENT PASS", "KEEPER SPEECH RENDER PASS",
                   f"KEEPER SPEECH COMPATIBILITY PASS mode={mode}"):
        if marker not in log:
            raise AssertionError("Missing hosted evidence: " + marker)
    if "CHECK FAILED" in log or "OutOfMemoryError" in log:
        raise AssertionError("Failure in " + mode)
    captures = sorted(Path(f"build/dialogue-evidence/{mode}").glob("*.png"))
    if not captures:
        raise AssertionError("Missing actual in-game capture for " + mode)
    evidence[mode] = {
        "markers": [line for line in log.splitlines() if " PASS" in line],
        "screenshots": [{"path": str(p), "sha256": sha256(p.read_bytes()).hexdigest()} for p in captures],
    }
report = {
    **scope,
    "workflow_run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "environment": "Public GitHub-hosted ubuntu-24.04, Java 21, NeoForge 21.1.249",
    "reference": {"file": "chatbubbles-1.1.1+1.21.1.jar",
                  "sha256": "9cfc0de144d71462626063440b13b35a55147c3f9881589b93d7ef0792921986",
                  "role": "Disposable fixture runtime only; absent from the delivered UwU JAR"},
    "evidence": evidence,
    "limitations": ["Boundary recipients are packet-capturing in-process server players; the visible client is a native Minecraft client.",
                    "Disposable hosted worlds only; no user profile or existing world was accessed.",
                    "The independent active weapon branch is not included."],
}
(folder / "provenance.json").write_text(json.dumps(report, indent=2) + "\n")
(folder / "villager-dialogue.patch").write_bytes(subprocess.check_output(
    ["git", "diff", "--binary", scope["baseline_source_sha"], "HEAD"]))
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write("## Built-in UwU villager dialogue\n\n")
    summary.write(f"Source: `{scope['source_sha']}`\n\n")
    summary.write(f"Baseline: `{scope['baseline_source_sha']}`\n\n")
    summary.write(f"JAR SHA-256: `{scope['candidate_jar_sha256']}`\n\n")
    for mode, record in evidence.items():
        summary.write(f"### {mode}\n\n")
        for marker in record["markers"]:
            summary.write(f"- {marker}\n")
print("KEEPER SPEECH PROVENANCE PASS")
