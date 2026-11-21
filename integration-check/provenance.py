"""Collect evidence only when every suite passed against the same frozen candidate."""
from argparse import ArgumentParser
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import struct

import verify


CLIENT_LOGS = ("build/cave-client.log", "build/cupid-lantern-client.log")
REQUIRED_MARKERS = {
    "build/cave-client.log": (
        "JADE REACH FLUID CAPTIONS PASS",
        "JADE REACH BERRY CAPTIONS PASS",
        "CAVE VINE MODELS AND HARVEST PASS",
        "CAVE FLORA PALETTE PASS",
    ),
    "build/cupid-lantern-client.log": (
        "LANTERN COVER CLIENT PASS",
        "CUPID HELD SCALE PASS",
        "CUPID CREATIVE CHOICES PASS",
        "CUPID CREATIVE AND CLIENT POSES PASS",
        "CUPID HOTBAR RENDER PASS",
        "LANTERN OPENING CLIENT PASS",
    ),
    "build/dusk-check/fresh-forward.log": ("DUSK FRESH PASS order=forward",),
    "build/dusk-check/fresh-reverse.log": ("DUSK FRESH PASS order=reverse",),
    "build/dusk-check/fresh-restart.log": ("DUSK FRESH RESTART PASS",),
    "build/dusk-check/seed-legacy.log": ("DUSK LEGACY SEEDED",),
    "build/dusk-check/legacy.log": ("DUSK LEGACY PASS",),
    "build/dusk-check/legacy-restart.log": ("DUSK LEGACY RESTART PASS",),
    "build/shrine-check/shoulder.log": (
        "PETAL SHRINE SERVER PASS order=shoulder", "CUPID SHRINE RUNTIME PASS",
    ),
    "build/shrine-check/village.log": (
        "PETAL SHRINE SERVER PASS order=village", "CUPID SHRINE RUNTIME PASS",
    ),
    "build/waterfall-check/server.log": ("HEARTWATER WATERFALL PASS",),
}
EVIDENCE = Path("build/cupid-lantern-evidence")
PAGE_TWO = EVIDENCE / "lantern-story-page-two-first-manual-turn.png"
REQUIRED_IMAGES = (
    Path("build/cave-client/cave-flora-palette.png"),
    EVIDENCE / "hotbar-original-playtest9-revised.png",
    EVIDENCE / "lantern-story-page-one-natural.png",
    PAGE_TWO,
    EVIDENCE / "ordinary-book-native-reader.png",
)
FAILURE = re.compile(r"CHECK FAILED|OutOfMemoryError|single server tick has taken|Error upgrading chunk", re.IGNORECASE)


def image_record(path):
    raw = path.read_bytes()
    verify.require(len(raw) > 32 and raw[:8] == b"\x89PNG\r\n\x1a\n" and raw[12:16] == b"IHDR",
                   f"Missing or malformed hosted image: {path}")
    width, height = struct.unpack(">II", raw[16:24])
    verify.require(width >= 320 and height >= 200, f"Unexpectedly small hosted image: {path}")
    return {"sha256": sha256(raw).hexdigest(), "width": width, "height": height}


def collect(candidate, baseline):
    head = verify.environment()
    digest = verify.candidate_identity(candidate)
    baseline_digest = sha256(baseline.read_bytes()).hexdigest()
    verify.require(baseline_digest == verify.BASELINE_JAR_SHA256, "Original Playtest 9 baseline changed after validation")
    scope_path = Path("build/integration-check/scope.json")
    scope_bytes = scope_path.read_bytes()
    scope = json.loads(scope_bytes)
    for key, expected in {
        "source_sha": head,
        "baseline_source_sha": verify.BASELINE,
        "cupid_book_source_sha": verify.CUPID,
        "caves_dusk_source_sha": verify.CAVES,
        "version": verify.VERSION,
        "candidate_jar": str(candidate.resolve()),
        "candidate_jar_sha256": digest,
        "baseline_jar_sha256": baseline_digest,
        "helper_mods_excluded": True,
    }.items():
        verify.require(scope.get(key) == expected, f"Final evidence does not match the frozen scope record: {key}")

    logs = {}
    evidence = {}
    for name, markers in REQUIRED_MARKERS.items():
        raw = Path(name).read_bytes()
        log = raw.decode(errors="replace")
        logs[name] = log
        verify.require(FAILURE.search(log) is None, f"Failure text remains in required suite log: {name}")
        for marker in markers:
            verify.require(marker in log, f"Missing required hosted evidence in {name}: {marker}")
        evidence[name] = {
            "sha256": sha256(raw).hexdigest(),
            "required_markers": list(markers),
            "result_lines": [line for line in log.splitlines()
                             if " PASS" in line or "DUSK FRESH PLAN " in line or "DUSK LEGACY SEEDED" in line],
        }

    identity = f"UWU PACKAGED CANDIDATE PASS sha256={digest} path={candidate.resolve()}"
    for name in CLIENT_LOGS:
        identities = [line for line in logs[name].splitlines() if "UWU PACKAGED CANDIDATE PASS" in line]
        verify.require(len(identities) == 1 and identity in identities[0],
                       f"Client did not load the exact frozen production JAR: {name}")
        evidence[name]["packaged_candidate_identity"] = identities[0]

    # Preserve the existing chunk-order equality gates in the final evidence packet too.
    plans = []
    for name in ("build/dusk-check/fresh-forward.log", "build/dusk-check/fresh-reverse.log"):
        rows = [line.split("DUSK FRESH PLAN ", 1)[1] for line in logs[name].splitlines() if "DUSK FRESH PLAN " in line]
        verify.require(len(rows) == 1, f"Missing or ambiguous native Dusk plan: {name}")
        plans.append(rows[0])
    verify.require(plans[0] == plans[1], "Dusk plans differ between native chunk orders")
    shrine_anchors = []
    for order in ("shoulder", "village"):
        marker = f"PETAL SHRINE SERVER PASS order={order} anchor="
        rows = [line.split(marker, 1)[1] for line in logs[f"build/shrine-check/{order}.log"].splitlines() if marker in line]
        verify.require(len(rows) == 1, f"Missing or ambiguous shrine result: {order}")
        shrine_anchors.append(rows[0])
    verify.require(shrine_anchors[0] == shrine_anchors[1], "Shrine placement differs between native chunk orders")

    images = {str(path): image_record(path) for path in REQUIRED_IMAGES}
    verify.require(Path("build/cave-client/cave-flora-palette.txt").stat().st_size > 0, "Cave palette labels are missing")
    verify.require((EVIDENCE / "frames.tsv").stat().st_size > 0, "Hosted frame metadata is missing")
    report = {
        "source_sha": head,
        "baseline_source_sha": verify.BASELINE,
        "cupid_book_source_sha": verify.CUPID,
        "caves_dusk_source_sha": verify.CAVES,
        "version": verify.VERSION,
        "jar_filename": candidate.name,
        "jar_path": str(candidate.resolve()),
        "jar_sha256": digest,
        "baseline_jar_sha256": baseline_digest,
        "scope_record_sha256": sha256(scope_bytes).hexdigest(),
        "workflow_run_url": (f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/"
                             f"{os.environ['GITHUB_RUN_ID']}"),
        "validation_environment": "Public GitHub-hosted runner, Java 21, NeoForge 21.1.249",
        "client_runtime_identity": "Both focused clients loaded the same frozen production JAR by canonical path and SHA-256",
        "first_manual_story_page_two": str(PAGE_TWO),
        "evidence": evidence,
        "required_images": images,
        "fixture_scope": "Disposable hosted client/server fixtures; no user world or external server accessed",
    }
    folder = Path("build/integration-check")
    (folder / "provenance.json").write_text(json.dumps(report, indent=2) + "\n")
    (folder / "integration.patch").write_bytes(verify.git("diff", "--binary", verify.BASELINE, "HEAD"))
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write("## Combined Cupid/book and caves/Dusk validation\n\n")
        summary.write(f"Source: `{head}`\n\nVersion: `{verify.VERSION}`\n\n")
        summary.write(f"Frozen JAR SHA-256: `{digest}`\n\n")
        summary.write(f"Exact Playtest 9 baseline SHA-256: `{baseline_digest}`\n\n")
        summary.write("Both focused clients verified the loaded production JAR path and digest.\n\n")
        summary.write(f"First manual story-page-two capture: `{PAGE_TWO.name}`\n\n")
        for name, item in evidence.items():
            summary.write(f"- `{name}`: " + "; ".join(item["required_markers"]) + "\n")
    print("UWU INTEGRATION PROVENANCE PASS every focused suite, both packaged client identities, manual page-two capture")
    print(f"UWU INTEGRATION VERIFIED CANDIDATE SHA256 {digest}")
    return report


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    args = parser.parse_args()
    collect(args.candidate, args.baseline)
