#!/usr/bin/env python3
"""Check the approved source patch before the one hosted product compilation."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

BASE = "bb5c6a926c24fa0433f1d63e10e6a01718f1d9bd"
ZIP = "827ad25f615acd1176663cf7f146b6b1060be1fcff446b1118c6cc4d9275dbdf"
PATCH = "2e22a11083fcd85710d9d938197cc1792c764c3ba5d623bdd0996f7e46f7593d"

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def check(condition, message):
    if not condition:
        raise SystemExit("COMBINED SOURCE FAILED: " + message)

check(os.environ.get("GITHUB_ACTIONS") == "true", "hosted Actions execution required")
manifest = json.loads(Path("combined-check/source-manifest.json").read_text())
check(manifest["base_commit"] == BASE and manifest["zip_sha256"].lower() == ZIP
      and manifest["patch_sha256"].lower() == PATCH, "handoff provenance changed")
records = manifest["files"]
paths = [r["path"] for r in records]
check(len(paths) == 73 and len(set(paths)) == 73, "expected exactly 73 approved paths")
subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], check=True)
actual = set(git("diff", "--name-only", BASE, "HEAD", "--", "src/main").splitlines())
check(actual == set(paths), "product scope differs: " + repr(actual.symmetric_difference(paths)))
for record in records:
    path = record["path"]
    check(path.startswith("src/main/"), "unexpected product path " + path)
    check(git("rev-parse", "HEAD:" + path) == record["after"], "candidate blob differs: " + path)
    if record.get("before"):
        check(git("rev-parse", BASE + ":" + path) == record["before"], "base blob differs: " + path)
    else:
        check(subprocess.run(["git", "cat-file", "-e", BASE + ":" + path],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0,
              "new path already existed: " + path)
    check(hashlib.sha256(Path(path).read_bytes()).hexdigest() == record["sha256"],
          "working-tree bytes differ: " + path)
allowed = ("combined-check/", "visual-check/", "hotbar-check/")
other = set(git("diff", "--name-only", BASE, "HEAD").splitlines()) - set(paths)
check(all(p.startswith(allowed) or p == ".github/workflows/combined-approved.yml" for p in other),
      "unapproved non-product changes: " + repr(sorted(p for p in other if not p.startswith(allowed)
                        and p != ".github/workflows/combined-approved.yml")))
print("COMBINED SOURCE PASS approved_product_paths=73 base=" + BASE)
