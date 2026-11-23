#!/usr/bin/env python3
"""Seal one compiled product; subsequent QA uses the exact frozen JAR."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path("build/combined-candidate")
BASE = "c1c0802be13d0cd5a01149ffa357018f7a863c5c"

def check(condition, message):
    if not condition:
        raise SystemExit("COMBINED CANDIDATE FAILED: " + message)

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def output(key, value):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(f"{key}={value}\n")

check(os.environ.get("GITHUB_ACTIONS") == "true", "Actions only")
mode = sys.argv[1]
manifest = json.loads(Path("combined-check/source-manifest.json").read_text())
if mode == "mode":
    reuse_file = Path("combined-check/reuse-candidate.json")
    if reuse_file.exists():
        reuse = json.loads(reuse_file.read_text())
        check(isinstance(reuse.get("run_id"), int) and reuse["run_id"] > 0, "bad reuse run")
        check(re.fullmatch("[0-9a-f]{40}", reuse.get("source_commit", "")), "bad reuse source")
        check(re.fullmatch("[0-9a-f]{64}", reuse.get("jar_sha256", "")), "bad reuse digest")
        output("mode", "reuse")
        output("run_id", reuse["run_id"])
        output("artifact_name", "uwu-combined-candidate-" + reuse["source_commit"])
    else:
        output("mode", "build")
    raise SystemExit(0)

ROOT.mkdir(parents=True, exist_ok=True)
if mode == "build":
    jars = list(Path("build/libs").glob("UwU-Dimension-*.jar"))
    check(len(jars) == 1, "expected one compiled product: " + repr(jars))
    source_commit = git("rev-parse", "HEAD")
    filename = "UwU-Dimension-1.21.1-combined-uwuzi-breakfast-chakrams-" + source_commit[:12] + ".jar"
    candidate = ROOT / filename
    shutil.copyfile(jars[0], candidate)
    report = {
        "source_commit": source_commit,
        "base_commit": BASE,
        "product_tree": git("rev-parse", "HEAD:src/main"),
        "build_gradle_blob": git("rev-parse", "HEAD:build.gradle"),
        "handoffs": manifest["handoffs"],
        "approved_cues": manifest["public_audio_authorization"]["cues"],
        "jar_filename": filename,
        "jar_sha256": digest(candidate),
        "build_run_id": os.environ["GITHUB_RUN_ID"],
        "build_run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "build_run_url": os.environ["GITHUB_SERVER_URL"] + "/" + os.environ["GITHUB_REPOSITORY"] + "/actions/runs/" + os.environ["GITHUB_RUN_ID"],
        "product_builds": 1,
        "artifact_status": "Combined test candidate; consult exact-JAR hosted acceptance before installation",
        "exclusions": ["original MP3", "unrelated private files", "unapproved weapon/icon edits"],
    }
    (ROOT / "BUILD_PROVENANCE.json").write_text(json.dumps(report, indent=2) + "\n")
    shutil.copyfile("combined-check/source-manifest.json", ROOT / "SOURCE_MANIFEST.json")
elif mode in ("reuse", "check"):
    report = json.loads((ROOT / "BUILD_PROVENANCE.json").read_text())
    candidate = ROOT / report["jar_filename"]
    if mode == "reuse":
        reuse = json.loads(Path("combined-check/reuse-candidate.json").read_text())
        check(report["source_commit"] == reuse["source_commit"], "reused source differs")
        check(report["jar_sha256"] == reuse["jar_sha256"], "reused digest differs")
        check(str(report["build_run_id"]) == str(reuse["run_id"]), "reused run differs")
else:
    raise SystemExit("Expected mode, build, reuse or check")

check(candidate.is_file() and candidate.parent == ROOT, "missing or unsafe candidate name")
check(report["base_commit"] == BASE, "candidate base differs")
check(report["handoffs"] == manifest["handoffs"], "candidate handoffs differ")
check(git("rev-parse", "HEAD:src/main") == report["product_tree"], "QA product source differs")
check(git("rev-parse", "HEAD:build.gradle") == report["build_gradle_blob"], "QA product build configuration differs")
check(digest(candidate) == report["jar_sha256"], "candidate bytes changed")
check(list(ROOT.glob("*.jar")) == [candidate], "ambiguous candidate files")
check(json.loads((ROOT / "SOURCE_MANIFEST.json").read_text()) == manifest, "manifest differs from sealed candidate")
with zipfile.ZipFile(candidate) as archive:
    names = archive.namelist()
    check(not any(p.startswith(("dev/uwu/hotbarcheck/", "dev/uwu/weaponcheck/", "dev/uwu/professioncheck/",
                                "dev/uwu/pocketcheck/", "dev/uwu/breakfastcheck/")) for p in names), "test classes leaked into product")
    check(not any(p.lower().endswith((".mp3", ".wav")) for p in names), "excluded original audio format")
    authorized = {p.removeprefix("src/main/resources/"): h for p, h in report["approved_cues"].items()}
    actual_cues = {p for p in names if p.startswith("assets/uwudimension/sounds/uwuzi/") and not p.endswith("/")}
    check(actual_cues == set(authorized), "derived-cue scope differs")
    for name, expected in authorized.items():
        check(hashlib.sha256(archive.read(name)).hexdigest() == expected, "cue hash differs: " + name)
    for record in manifest["files"]:
        if record["path"].startswith("src/main/resources/"):
            name = record["path"].removeprefix("src/main/resources/")
            check(hashlib.sha256(archive.read(name)).hexdigest() == record["sha256"], "packaged resource differs: " + name)
(ROOT / "SHA256SUMS.txt").write_text(report["jar_sha256"] + "  " + candidate.name + "\n")
output("hash", report["jar_sha256"])
output("source_commit", report["source_commit"])
output("jar_filename", candidate.name)
print("COMBINED SEALED CANDIDATE PASS sha256=" + report["jar_sha256"] + " source=" + report["source_commit"])
