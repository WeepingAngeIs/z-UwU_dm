"""Hosted-only source and packaged scope gate for the isolated Dusk candidate."""
from argparse import ArgumentParser
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

BASELINE = "1f489c5308b686a30672ec19b671a64534eb28a4"
BASELINE_DIGEST = "0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f"
BASELINE_VERSION = "1.5.0-beta.13-combined-rc.2-playtest.9-integrated.1"
VERSION = BASELINE_VERSION + "-dusk-frequency.1"
JAVA = "src/main/java/"
RESOURCES = "src/main/resources/"
MOD_INFO = RESOURCES + "META-INF/neoforge.mods.toml"
NATIVE = JAVA + "dev/uwu/dimension/"
PRODUCTION = {"build.gradle", MOD_INFO} | {NATIVE + name + ".java" for name in (
    "AcquisitionPolicy", "SettlementLayout", "BlossomVillage", "HeartwaterFeature", "SakuraForest")}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(["git", *args])


def exact_hook(name, edits):
    path = NATIVE + name + ".java"
    expected = git("show", BASELINE + ":" + path)
    for before, after in edits:
        require(expected.count(before) == 1, "Native hook anchor changed: " + path)
        expected = expected.replace(before, after, 1)
    require(Path(path).read_bytes() == expected, "Unrelated native behavior changed: " + path)


def method(source, signature):
    """Select an existing brace-balanced method for an exact preservation comparison."""
    start = source.index(signature)
    opening = source.index(b"{", start)
    depth = 1
    end = opening + 1
    while depth:
        require(end < len(source), "Unterminated method " + signature.decode())
        depth += (source[end] == 123) - (source[end] == 125)
        end += 1
    return source[start:end]


def verify(candidate, baseline, classes):
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Run validation only on GitHub Actions")
    require(os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted", "Use a free public GitHub-hosted runner")
    require(os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm", "Unexpected repository")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    require(event.get("repository", {}).get("private") is False, "A public repository is required")
    head = git("rev-parse", "HEAD").decode().strip()
    require(head == os.environ["GITHUB_SHA"], "Checkout differs from the advertised source SHA")
    subprocess.run(["git", "merge-base", "--is-ancestor", BASELINE, "HEAD"], check=True)
    subprocess.run(["git", "diff", "--exit-code", "HEAD", "--"], check=True)
    require(not git("ls-files", "--others", "--exclude-standard", "--", "src/main/java", "src/main/resources"),
            "Untracked production files appeared")
    changed = set(git("diff", "--name-only", BASELINE, "HEAD").decode().splitlines())
    for path in changed:
        require(path in PRODUCTION or path.startswith(("dusk-frequency-check/", "dusk-frequency-ci/"))
                or path == ".github/workflows/dusk-frequency.yml", "Changed file outside Dusk scope: " + path)
    require(PRODUCTION <= changed, "Expected Dusk production files are missing")
    for path in ("build.gradle", MOD_INFO):
        original = git("show", BASELINE + ":" + path)
        require(original.count(BASELINE_VERSION.encode()) == 1, "Ambiguous baseline version")
        require(Path(path).read_bytes() == original.replace(BASELINE_VERSION.encode(), VERSION.encode(), 1),
                "Dependency/build metadata changed beyond the candidate version: " + path)

    exact_hook("HeartwaterFeature", [
        (b'        if(!site.usable())return strata;\r\n',
         b'        if(!site.usable())return strata;\r\n        SettlementLayout.beginDuskGeneration(level,site,pos);\r\n'),
        (b'        if(!site.usable())return;\r\n        var valley=new Valley(level,chunk,site);',
         b'        if(!site.usable())return;\r\n        SettlementLayout.beginDuskGeneration(level,site,chunk.getPos());\r\n        var valley=new Valley(level,chunk,site);'),
        (b'                for(boolean dusk:new boolean[]{false,true})BlossomVillage.plan(level.getLevel(),site,dusk).ifPresent(p->{',
         b'                // Pure authored reservations include a possible Dusk village even\r\n'
         b'                // when its current roll is denied. They remain valid after live edits.\r\n'
         b'                for(boolean dusk:new boolean[]{false,true})(planning()?BlossomVillage.potentialPlan(level.getLevel(),site,dusk)\r\n'
         b'                        :BlossomVillage.plan(level.getLevel(),site,dusk)).ifPresent(p->{'),
    ])
    exact_hook("SakuraForest", [
        (b'            var village=BlossomVillage.plan(level,site,dusk);',
         b'            // Keep cached tree reservations stable when the future-site chance is edited.\r\n'
         b'            var village=BlossomVillage.potentialPlan(level,site,dusk);'),
    ])
    layout = NATIVE + "SettlementLayout.java"
    before, after = git("show", BASELINE + ":" + layout), Path(layout).read_bytes()
    unchanged_methods = (b"private int legacyMask(", b"static boolean admits(", b"private boolean duskFaction(",
                         b"private static boolean duskFootprint(", b"private static boolean latticeCell(",
                         b"private static boolean lattice(", b"static int stride(", b"static boolean historical(",
                         b"private static void indexPrevious(", b"private static void loadRoomSnapshot(")
    for signature in unchanged_methods:
        require(method(before, signature) == method(after, signature),
                "Existing faction, spacing or historical rule changed: " + signature.decode())
    village = NATIVE + "BlossomVillage.java"
    before, after = git("show", BASELINE + ":" + village), Path(village).read_bytes()
    old_design = method(before, b"private static Optional<Plan> design(")
    old_design = old_design.replace(b"boolean dusk){", b"boolean dusk,int attempts){", 1)
    old_design = old_design.replace(b"attempt<32;", b"attempt<attempts;", 1)
    require(method(after, b"private static Optional<Plan> design(") == old_design,
            "Terrain survey safety or original random sequence changed")
    marker = b"    static void place(HeartwaterFeature.Valley v,Plan p){"
    require(before[before.index(marker):] == after[after.index(marker):], "Village architecture or residents changed")

    candidate = candidate.resolve()
    digest = sha256(candidate.read_bytes()).hexdigest()
    require(candidate == Path(os.environ["UWU_JAR"]).resolve(), "A suite selected a different candidate")
    require(digest == os.environ["UWU_EXPECTED_JAR_SHA256"], "Frozen candidate hash changed")
    require(sha256(baseline.read_bytes()).hexdigest() == BASELINE_DIGEST, "Wrong integrated baseline artifact")
    allowed_classes = {p[len(JAVA):].removesuffix(".java") for p in PRODUCTION if p.startswith(JAVA)}
    preserved, altered = [], []
    with ZipFile(candidate) as new, ZipFile(baseline) as old:
        new_names = [i.filename for i in new.infolist() if not i.is_dir()]
        old_names = [i.filename for i in old.infolist() if not i.is_dir()]
        require(len(new_names) == len(set(new_names)) and len(old_names) == len(set(old_names)), "Duplicate JAR entries")
        require(new.testzip() is None and old.testzip() is None, "Corrupt JAR")
        for name in sorted(set(new_names) | set(old_names)):
            same = name in new_names and name in old_names and new.read(name) == old.read(name)
            allowed = name == "META-INF/neoforge.mods.toml" or (name.endswith(".class")
                    and name.removesuffix(".class").split("$", 1)[0] in allowed_classes)
            if not same:
                require(allowed, "Packaged content outside Dusk scope changed: " + name)
                altered.append(name)
            elif not allowed:
                preserved.append(name)
        require(not any(name.startswith(("dev/uwu/duskfrequency/", "dev/unyielding/", "dusk-frequency-check/"))
                        for name in new_names), "Fixture or companion code leaked into the UwU product")
        require(b"unyieldingquests" not in new.read("META-INF/neoforge.mods.toml").lower(),
                "Unyielding must not become a required UwU dependency")
        require(f'version="{VERSION}"'.encode() in new.read("META-INF/neoforge.mods.toml"), "Wrong version")
        for row in git("ls-files", "-z", "src/main/resources").split(b"\0"):
            if row:
                source = Path(row.decode())
                name = source.as_posix()[len(RESOURCES):]
                require(name in new_names and new.read(name) == source.read_bytes(), "Stale resource: " + name)
        packaged = {name for name in new_names if name.endswith(".class")}
        compiled = {p.relative_to(classes).as_posix() for p in classes.rglob("*.class")}
        require(packaged == compiled, "Packaged main-class inventory differs from the current compilation")
        for name in packaged:
            require(new.read(name) == (classes / name).read_bytes(), "Stale packaged class: " + name)
    report = {
        "source_sha": head, "baseline_source_sha": BASELINE, "baseline_jar_sha256": BASELINE_DIGEST,
        "candidate_jar_sha256": digest, "version": VERSION, "changed_source_files": sorted(changed),
        "approved_production_files": sorted(PRODUCTION), "changed_jar_entries": altered,
        "unchanged_entries_outside_dusk_generation": len(preserved),
        "original_faction_spacing_history_and_survey_safety_preserved": True,
        "architecture_residents_assets_weapons_books_dialogue_unchanged": True,
        "companion_patch_is_separate_and_not_bundled": True,
    }
    folder = Path("build/dusk-frequency-ci")
    folder.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(report, indent=2) + "\n").encode()
    record = folder / "scope.json"
    if record.exists():
        require(record.read_bytes() == encoded, "Source/scope/candidate identity changed during validation")
    else:
        record.write_bytes(encoded)
    print(f"DUSK FREQUENCY SCOPE PASS unchanged={len(preserved)} source={head} jar={digest}")


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--classes", type=Path, default=Path("build/classes/java/main"))
    args = parser.parse_args()
    verify(args.candidate, args.baseline, args.classes)
