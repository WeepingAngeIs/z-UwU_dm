#!/usr/bin/env python3
"""Seal or revalidate one immutable product JAR; QA retries never rebuild it."""
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
BASE = "bb5c6a926c24fa0433f1d63e10e6a01718f1d9bd"

def check(condition, message):
    if not condition:
        raise SystemExit("COMBINED CANDIDATE FAILED: " + message)

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def output(key, value):
    with open(os.environ["GITHUB_OUTPUT"], "a") as f:
        f.write(f"{key}={value}\n")

check(os.environ.get("GITHUB_ACTIONS") == "true", "Actions only")
mode = sys.argv[1]
if mode == "mode":
    reuse_file = Path("combined-check/reuse-candidate.json")
    if reuse_file.exists():
        reuse = json.loads(reuse_file.read_text())
        check(isinstance(reuse.get("run_id"), int) and reuse["run_id"] > 0, "bad reuse run_id")
        check(re.fullmatch("[0-9a-f]{40}", reuse.get("source_commit", "")), "bad reuse source_commit")
        check(re.fullmatch("[0-9a-f]{64}", reuse.get("jar_sha256", "")), "bad reuse jar_sha256")
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
    filename = "UwU-Dimension-1.21.1-combined-approved-" + source_commit[:12] + ".jar"
    candidate = ROOT / filename
    shutil.copyfile(jars[0], candidate)
    manifest = json.loads(Path("combined-check/source-manifest.json").read_text())
    report = {
        "source_commit": source_commit,
        "base_commit": BASE,
        "product_tree": git("rev-parse", "HEAD:src/main"),
        "zip_sha256": manifest["zip_sha256"],
        "patch_sha256": manifest["patch_sha256"],
        "jar_filename": filename,
        "jar_sha256": digest(candidate),
        "build_run_id": os.environ["GITHUB_RUN_ID"],
        "build_run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "build_run_url": os.environ["GITHUB_SERVER_URL"] + "/" + os.environ["GITHUB_REPOSITORY"]
                         + "/actions/runs/" + os.environ["GITHUB_RUN_ID"],
        "product_builds": 1,
        "product_version_unchanged": True,
        "artifact_status": "Candidate; consult hosted acceptance results before use",
        "exclusions": ["separate UwUzi prototype", "private clock audio", "unapproved icon or weapon edits"],
    }
    (ROOT / "BUILD_PROVENANCE.json").write_text(json.dumps(report, indent=2) + "\n")
    shutil.copyfile("combined-check/source-manifest.json", ROOT / "SOURCE_MANIFEST.json")
elif mode in ("reuse", "check"):
    report = json.loads((ROOT / "BUILD_PROVENANCE.json").read_text())
    candidate = ROOT / report["jar_filename"]
    if mode == "reuse":
        reuse = json.loads(Path("combined-check/reuse-candidate.json").read_text())
        check(report["source_commit"] == reuse["source_commit"], "reused source identity differs")
        check(report["jar_sha256"] == reuse["jar_sha256"], "reused digest differs")
        check(str(report["build_run_id"]) == str(reuse["run_id"]), "reused build run differs")
else:
    raise SystemExit("Expected mode, build, reuse or check")

check(candidate.is_file() and candidate.parent == ROOT, "missing or unsafe candidate name")
check(report["base_commit"] == BASE, "candidate base differs")
check(git("rev-parse", "HEAD:src/main") == report["product_tree"], "QA product source differs from sealed build")
check(digest(candidate) == report["jar_sha256"], "candidate bytes changed")
check(list(ROOT.glob("*.jar")) == [candidate], "ambiguous candidate files")
with zipfile.ZipFile(candidate) as archive:
    names = archive.namelist()
    check(not any(p.startswith(("dev/uwu/hotbarcheck/", "dev/uwu/weaponcheck/", "dev/uwu/professioncheck/"))
                  for p in names), "test code leaked into candidate")
    check(not any("uwuzi" in p.lower() for p in names), "UwUzi leaked into candidate")
    manifest = json.loads((ROOT / "SOURCE_MANIFEST.json").read_text())
    for record in manifest["files"]:
        if record["path"].startswith("src/main/resources/"):
            name = record["path"][len("src/main/resources/"):]
            check(hashlib.sha256(archive.read(name)).hexdigest() == record["sha256"],
                  "packaged approved resource differs: " + name)
(ROOT / "SHA256SUMS.txt").write_text(report["jar_sha256"] + "  " + candidate.name + "\n")
output("hash", report["jar_sha256"])
output("source_commit", report["source_commit"])
output("jar_filename", candidate.name)
print("COMBINED SEALED CANDIDATE PASS sha256=" + report["jar_sha256"] + " source=" + report["source_commit"])
