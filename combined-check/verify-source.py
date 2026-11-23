#!/usr/bin/env python3
"""Verify the three pinned handoffs before the single hosted product build."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

BASE = "c1c0802be13d0cd5a01149ffa357018f7a863c5c"
HANDOFFS = {
    "uwuzi": "687071e25616fbf6d9f2107c151b8827c2ba3ef7",
    "breakfast": "d33e34cdd812bf4c1b53a0308bd114055d2c931e",
    "clipping": "3681aacf39fb7675802c25814282aba71f15297f",
}

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def check(condition, message):
    if not condition:
        raise SystemExit("COMBINED SOURCE FAILED: " + message)

check(os.environ.get("GITHUB_ACTIONS") == "true", "hosted Actions required")
check(os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm", "wrong repository")
manifest = json.loads(Path("combined-check/source-manifest.json").read_text())
check(manifest["base_commit"] == BASE and manifest["handoffs"] == HANDOFFS, "handoff pins changed")
records = manifest["files"]
paths = {r["path"] for r in records}
check(len(records) == len(paths) == 60, "expected 60 distinct approved product paths")
subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
for ref in HANDOFFS.values():
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, ref], check=True)
actual = set(git("diff", "--name-only", BASE, "HEAD", "--", "src/main", "build.gradle").splitlines())
check(actual == paths, "product scope differs: " + repr(actual.symmetric_difference(paths)))
for record in records:
    path = record["path"]
    check(path.startswith("src/main/") or path == "build.gradle", "unexpected product path")
    check(git("rev-parse", "HEAD:" + path) == record["after"], "candidate blob differs: " + path)
    if record["before"]:
        check(git("rev-parse", BASE + ":" + path) == record["before"], "base blob differs: " + path)
    else:
        check(subprocess.run(["git", "cat-file", "-e", BASE + ":" + path],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0,
              "new path existed in base: " + path)
    check(hashlib.sha256(Path(path).read_bytes()).hexdigest() == record["sha256"], "working bytes differ: " + path)
    if record["handoff"] != "merged-language":
        check(git("rev-parse", HANDOFFS[record["handoff"]] + ":" + path) == record["after"],
              "path differs from pinned handoff: " + path)

lang_path = "src/main/resources/assets/uwudimension/lang/en_us.json"
base_lang = json.loads(git("show", BASE + ":" + lang_path))
expected_lang = dict(base_lang)
added = set()
for owner in ("uwuzi", "breakfast"):
    incoming = json.loads(git("show", HANDOFFS[owner] + ":" + lang_path))
    check(all(incoming.get(k) == v for k, v in base_lang.items()), "existing language keys changed")
    new = {k: v for k, v in incoming.items() if k not in base_lang}
    check(not added.intersection(new), "overlapping new language keys")
    expected_lang.update(new)
    added.update(new)
check(len(added) == 22 and json.loads(Path(lang_path).read_text()) == expected_lang, "language merge differs")

allowed_qa = ("combined-check/", "hotbar-check/", "visual-check/", "time-pocket-check/", "breakfast-check/")
other = set(git("diff", "--name-only", BASE, "HEAD").splitlines()) - paths
check(all(p.startswith(allowed_qa) or p in (".github/workflows/combined-approved.yml", "breakfast-acceptance.md")
          for p in other), "unapproved non-product paths: " + repr(sorted(other)))
audio = manifest["public_audio_authorization"]["cues"]
check(len(audio) == 3, "expected three authorized derived cues")
for path, digest in audio.items():
    check(hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, "authorized cue changed: " + path)
check(not any(p.suffix.lower() in (".mp3", ".wav") for p in Path("src/main").rglob("*")), "original or unrelated audio format")
print("COMBINED SOURCE PASS approved_product_paths=60 base=" + BASE)
