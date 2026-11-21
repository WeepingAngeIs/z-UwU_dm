"""Validate the approved integration and its frozen JAR on public GitHub Actions only."""
from argparse import ArgumentParser
from hashlib import sha256
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
from zipfile import ZipFile


BASELINE = "e16af6a2a1a00cb68291b6c9611d4604a0b20171"
CUPID = "9b1d9fdffd5e385ae4f18f8e49e05559679ec306"
CAVES = "c7f3026cec0f673c7b9eb45a39d26f880283f912"
BASELINE_VERSION = "1.5.0-beta.13-combined-rc.2-playtest.9"
VERSION = BASELINE_VERSION + "-integrated.1"
BASELINE_JAR_SHA256 = "7e402979865fa5e56edc5f24d26fff9704e99fa53b9f821b8e074241bb4cd9d8"
JAVA = "src/main/java/"
RESOURCES = "src/main/resources/"
REGISTRATION = JAVA + "dev/uwu/dimension/UwUDimension.java"
MIXINS = RESOURCES + "uwudimension.mixins.json"
MOD_INFO = RESOURCES + "META-INF/neoforge.mods.toml"
CLIENT_ROOT = "lotus-client-check/java/dev/uwu/lotusclientcheck/"
SHARED_PRODUCTION = {"build.gradle", REGISTRATION, MIXINS, MOD_INFO}
SHARED_INFRASTRUCTURE = {
    "lotus-client-check/client.gradle",
    "lotus-client-check/run.sh",
    CLIENT_ROOT + "LotusClientProbe.java",
}
LANTERN_PROBE = CLIENT_ROOT + "LanternOpeningProbe.java"
NEW_INFRASTRUCTURE = {
    CLIENT_ROOT + "CandidateJarProbe.java",
    ".github/workflows/uwu-combined-integration.yml",
}
FROZEN_CHECKSUM = Path("build/integration-check/candidate.sha256")
HELPER_PREFIXES = (
    "dev/uwu/lotusclientcheck/", "dev/uwu/duskcheck/",
    "dev/uwu/shrinecheck/", "dev/uwu/waterfallcheck/",
    "assets/uwu_lotus_client_check/", "integration-check/",
    "cupid-lantern-check/", "fallback-check/",
)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(["git", *args])


def blob(ref, path):
    return git("show", f"{ref}:{path}")


def tree(ref):
    result = {}
    for row in git("ls-tree", "-rz", "--full-tree", ref).split(b"\0"):
        if not row:
            continue
        metadata, name = row.split(b"\t", 1)
        result[name.decode()] = tuple(metadata.decode().split())
    return result


def differences(before, after):
    return {path for path in before.keys() | after.keys() if before.get(path) != after.get(path)}


def replace_once(source, before, after, label):
    require(source.count(before) == 1, f"Verified source anchor changed: {label}")
    return source.replace(before, after, 1)


def environment():
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Run integration checks only on GitHub Actions")
    require(not sys.flags.optimize, "Do not disable assertions: the retained geometry gate uses them")
    require(os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm", "Unexpected target repository")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    require(event.get("repository", {}).get("private") is False, "The runner must belong to the public repository")
    head = git("rev-parse", "HEAD").decode().strip()
    require(head == os.environ["GITHUB_SHA"], "Runner source differs from its advertised SHA")
    require(Path(git("rev-parse", "--show-toplevel").decode().strip()).resolve() == Path.cwd().resolve(),
            "Run the integration gate from the repository root")
    subprocess.run(["git", "merge-base", "--is-ancestor", BASELINE, "HEAD"], check=True)
    return head


def check_shared_production():
    # Both version files must otherwise remain byte-for-byte identical to Playtest 9.
    for path in ("build.gradle", MOD_INFO):
        expected = replace_once(blob(BASELINE, path), BASELINE_VERSION.encode(), VERSION.encode(), path)
        require(Path(path).read_bytes() == expected, f"Unexpected change beyond the combined version: {path}")

    # Compose the two independent registration additions without rewriting either class.
    anchor = b"        dev.uwu.dimension.lush.LushGeodes.register(bus);\r\n"
    berry = b"        dev.uwu.dimension.lush.UwUCaveBerries.register(bus);\r\n"
    expected = replace_once(blob(CUPID, REGISTRATION), anchor, anchor + berry, "berry registration")
    config_anchor = (b"        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,"
                     b"SacredSpring.CLIENT_SPEC,\"uwudimension-client.toml\");\r\n")
    cupid_config = (b"        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,"
                    b"CupidBowVisualConfig.SPEC,\"uwudimension-cupid-visuals-client.toml\");\r\n")
    from_caves = replace_once(blob(CAVES, REGISTRATION), config_anchor, config_anchor + cupid_config,
                             "Cupid client configuration")
    require(expected == from_caves, "Approved registration changes no longer compose independently")
    require(Path(REGISTRATION).read_bytes() == expected, "Combined registrations differ from the two approved additions")

    baseline = json.loads(blob(BASELINE, MIXINS))
    cupid = json.loads(blob(CUPID, MIXINS))
    caves = json.loads(blob(CAVES, MIXINS))
    require(cupid["mixins"] == baseline["mixins"] and caves["client"] == baseline["client"],
            "Verified mixin changes are no longer disjoint")
    require({k: v for k, v in cupid.items() if k != "client"} ==
            {k: v for k, v in baseline.items() if k != "client"}, "Unexpected Cupid common mixin metadata")
    require({k: v for k, v in caves.items() if k != "mixins"} ==
            {k: v for k, v in baseline.items() if k != "mixins"}, "Unexpected caves client mixin metadata")
    expected_mixins = dict(cupid)
    expected_mixins["mixins"] = caves["mixins"]
    require(json.loads(Path(MIXINS).read_bytes()) == expected_mixins,
            "Combined mixins must exactly preserve both approved lists and their order")
    require("CaveVinesHarvestMixin" in expected_mixins["mixins"]
            and "RealmVillagerFactoryMixin" not in expected_mixins["mixins"]
            and {"CupidBowHeldModelMixin", "LanternBookOpenMixin"} <= set(expected_mixins["client"]),
            "Missing required integration mixin composition")


def check_manual_page_capture():
    """The only change allowed to the prior book fixture is the reviewed page-two capture."""
    expected = blob(CUPID, LANTERN_PROBE)
    edits = (
        (b"            new Trial(Kind.ORDINARY, InteractionHand.MAIN_HAND));\n",
         b"            new Trial(Kind.ORDINARY, InteractionHand.MAIN_HAND));\n"
         b"    private static final String MANUAL_PAGE_TWO = \"lantern-story-page-two-first-manual-turn\";\n"),
        (b"    private boolean skipped;\n", b"    private boolean skipped;\n    private boolean awaitingManualPageCapture;\n"),
        (b"        require(page(reader) == 0, \"Book did not settle on actual story page one\");\n",
         b"        if (awaitingManualPageCapture) {\n"
         b"            require(page(reader) == 1, \"First manual page turn did not remain on actual story page two\");\n"
         b"            if (!CupidLanternVisualProbe.saved(MANUAL_PAGE_TWO)) return false;\n"
         b"            reader.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0);\n"
         b"            require(page(reader) == 0, \"Native previous-page navigation failed\");\n"
         b"            advance(client, trial);\n"
         b"            return false;\n"
         b"        }\n"
         b"        require(page(reader) == 0, \"Book did not settle on actual story page one\");\n"),
        (b"        if (clientTicks - readerAt < 3) return false;\n",
         b"        if (clientTicks - readerAt < 3) return false;\n"
         b"        if (trial.kind == Kind.NATURAL && !CupidLanternVisualProbe.saved(\"lantern-story-page-one-natural\")) return false;\n"),
        (b"        require(page(reader) == 1, \"Native next-page navigation failed\");\n",
         b"        require(page(reader) == 1, \"Native next-page navigation failed\");\n"
         b"        if (trial.kind == Kind.NATURAL) {\n"
         b"            // Turn exactly once, then hold page two across client ticks until a rendered frame is saved.\n"
         b"            awaitingManualPageCapture = true;\n"
         b"            capture(MANUAL_PAGE_TWO);\n"
         b"            return false;\n"
         b"        }\n"),
        (b"        skipped = false;\n", b"        skipped = false;\n        awaitingManualPageCapture = false;\n"),
    )
    for index, (before, after) in enumerate(edits):
        expected = replace_once(expected, before, after, f"manual page-two fixture addition {index + 1}")
    require(Path(LANTERN_PROBE).read_bytes() == expected,
            "The book fixture changed beyond the approved first manual page-two capture")


def source_scope():
    head_sha = environment()
    # Tests and infrastructure must also remain the advertised committed source.
    subprocess.run(["git", "diff", "--exit-code", "HEAD", "--"], check=True)
    require(not git("ls-files", "--others", "--exclude-standard", "--", "src/main/java", "src/main/resources"),
            "Untracked production source appeared during validation")
    baseline, cupid, caves, current = (tree(ref) for ref in (BASELINE, CUPID, CAVES, "HEAD"))
    cupid_delta, caves_delta = differences(baseline, cupid), differences(baseline, caves)
    require(cupid_delta & caves_delta == SHARED_PRODUCTION | SHARED_INFRASTRUCTURE,
            "The exact approved commits no longer have the expected seven shared files")
    combined_delta = cupid_delta | caves_delta
    mutable = SHARED_PRODUCTION | SHARED_INFRASTRUCTURE | NEW_INFRASTRUCTURE | {LANTERN_PROBE}
    for path in baseline.keys() | cupid.keys() | caves.keys() | current.keys():
        if path in mutable or path.startswith("integration-check/"):
            require(path in current and current[path][:2] == ("100644", "blob"),
                    f"Required integration source missing or not a regular tracked file: {path}")
            continue
        expected = cupid.get(path) if path in cupid_delta else caves.get(path) if path in caves_delta else baseline.get(path)
        require(current.get(path) == expected,
                f"Source differs from its exact approved commit (or required deletion): {path}")
    for path in NEW_INFRASTRUCTURE | {"integration-check/verify.py", "integration-check/provenance.py"}:
        require(path in current, f"Required integration helper is missing: {path}")
    check_shared_production()
    check_manual_page_capture()
    subprocess.run(["git", "-c", "core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol",
                    "diff", "--check", BASELINE, "HEAD"], check=True)
    production = {path for path in combined_delta if path == "build.gradle" or path.startswith((JAVA, RESOURCES))}
    report = {
        "source_sha": head_sha,
        "baseline_source_sha": BASELINE,
        "cupid_book_source_sha": CUPID,
        "caves_dusk_source_sha": CAVES,
        "version": VERSION,
        "approved_production_files": sorted(production),
        "shared_production_files": sorted(SHARED_PRODUCTION),
        "changed_source_files": sorted(differences(baseline, current)),
    }
    print("UWU INTEGRATION SOURCE PASS exact one-sided deltas, required deletions, shared registrations and mixins")
    return report, production, current


def candidate_identity(candidate):
    candidate = candidate.resolve()
    digest = sha256(candidate.read_bytes()).hexdigest()
    require(digest == os.environ.get("UWU_EXPECTED_JAR_SHA256"), "Frozen candidate JAR digest changed")
    require(candidate == Path(os.environ["UWU_JAR"]).resolve(), "A suite selected a different candidate path")
    lines = [line for line in FROZEN_CHECKSUM.read_text().splitlines() if line.strip()]
    require(len(lines) == 1 and lines[0].split()[0] == digest, "Frozen candidate checksum record does not match")
    return digest


def load_cupid_gate():
    path = Path("cupid-lantern-check/verify.py")
    # source_scope has already checked this module's bytes against the verified Cupid commit.
    spec = importlib.util.spec_from_file_location("approved_cupid_lantern_gate", path)
    require(spec is not None and spec.loader is not None, "Cannot import the preserved Cupid geometry gate")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def verify(candidate, baseline, classes):
    report, production, current = source_scope()
    digest = candidate_identity(candidate)
    baseline_digest = sha256(baseline.read_bytes()).hexdigest()
    require(baseline_digest == BASELINE_JAR_SHA256,
            "Exact original Playtest 9 JAR unavailable or changed: expected SHA-256 " + BASELINE_JAR_SHA256
            + "; received " + baseline_digest + ". A source rebuild is not the required original artifact.")
    changed_resources = {path[len(RESOURCES):] for path in production if path.startswith(RESOURCES)}
    changed_classes = {path[len(JAVA):].removesuffix(".java") for path in production if path.startswith(JAVA)}

    def allowed_entry(name):
        return name in changed_resources or (name.endswith(".class")
                and name.removesuffix(".class").split("$", 1)[0] in changed_classes)

    with ZipFile(candidate) as new, ZipFile(baseline) as old:
        require(new.testzip() is None and old.testzip() is None, "Corrupt candidate or baseline JAR")
        new_entries = [entry.filename for entry in new.infolist() if not entry.is_dir()]
        old_entries = [entry.filename for entry in old.infolist() if not entry.is_dir()]
        require(len(new_entries) == len(set(new_entries)) and len(old_entries) == len(set(old_entries)),
                "Duplicate JAR entries")
        require(not any(name.startswith(HELPER_PREFIXES) for name in new_entries), "Hosted check helpers leaked into the production JAR")
        require(f'version="{VERSION}"'.encode() in new.read("META-INF/neoforge.mods.toml"), "Wrong packaged combined version")
        require(f'version="{BASELINE_VERSION}"'.encode() in old.read("META-INF/neoforge.mods.toml"), "Wrong baseline version")
        altered = []
        preserved = []
        for name in sorted(set(new_entries) | set(old_entries)):
            same = name in new_entries and name in old_entries and new.read(name) == old.read(name)
            if not same:
                require(allowed_entry(name), f"Packaged content outside the approved union changed: {name}")
                altered.append(name)
            elif not allowed_entry(name):
                preserved.append(name)

        for path in current:
            if path.startswith(RESOURCES):
                name = path[len(RESOURCES):]
                require(name in new_entries and new.read(name) == Path(path).read_bytes(),
                        f"Packaged resource differs from committed source: {name}")
        packaged_classes = {name for name in new_entries if name.endswith(".class")}
        compiled_classes = {path.relative_to(classes).as_posix() for path in classes.rglob("*.class")}
        require(packaged_classes == compiled_classes, "Packaged and freshly compiled main class inventories differ")
        for name in packaged_classes:
            require(new.read(name) == (classes / name).read_bytes(), f"Stale or different packaged class: {name}")
        for stem in changed_classes:
            if JAVA + stem + ".java" in current:
                require(stem + ".class" in packaged_classes, f"Approved production class absent from JAR: {stem}")
            else:
                require(not any(name == stem + ".class" or name.startswith(stem + "$") for name in packaged_classes),
                        f"Deleted production class remains packaged: {stem}")

        cupid_gate = load_cupid_gate()
        for suffix in cupid_gate.POSES:
            fixture = Path("lotus-client-check/resources/assets/uwu_lotus_client_check/models/item/"
                           f"playtest9_smooth_cupid_bow{suffix}.json")
            require(fixture.read_bytes() == blob(BASELINE, RESOURCES + cupid_gate.MODEL_PREFIX + suffix + ".json"),
                    f"Hotbar comparison fixture differs from the shipped Playtest 9 model: {fixture}")
        cupid_gate.geometry(new)

    report.update({
        "candidate_jar": str(candidate.resolve()),
        "candidate_jar_sha256": digest,
        "baseline_jar_sha256": baseline_digest,
        "preserved_jar_entries_outside_union": len(preserved),
        "changed_jar_entries": altered,
        "packaged_main_class_count": len(packaged_classes),
        "helper_mods_excluded": True,
        "bow_geometry_and_core_gate": "passed unchanged approved geometry()",
    })
    folder = Path("build/integration-check")
    folder.mkdir(parents=True, exist_ok=True)
    record = folder / "scope.json"
    encoded = (json.dumps(report, indent=2) + "\n").encode()
    if record.exists():
        require(record.read_bytes() == encoded, "The frozen integration scope/identity record changed during the suites")
    else:
        record.write_bytes(encoded)
    print(f"UWU INTEGRATION JAR PASS {len(preserved)} unchanged entries outside the approved union")
    print(f"UWU INTEGRATION JAR SHA256 {digest}")
    return report


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--classes", type=Path, default=Path("build/classes/java/main"))
    args = parser.parse_args()
    verify(args.candidate, args.baseline, args.classes)
