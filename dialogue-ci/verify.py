"""Verify the frozen dialogue-only candidate against integrated Playtest 9 in hosted CI."""
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
VERSION = BASELINE_VERSION + "-dialogue.1"
JAVA = "src/main/java/"
RESOURCES = "src/main/resources/"
MOD_INFO = RESOURCES + "META-INF/neoforge.mods.toml"
PRODUCTION = {
    "build.gradle", MOD_INFO,
    JAVA + "dev/uwu/dimension/Keeper.java",
    JAVA + "dev/uwu/dimension/KeeperSpeech.java",
    JAVA + "dev/uwu/dimension/SakuraAmbientDialogue.java",
    JAVA + "dev/uwu/dimension/UwUDimension.java",
    JAVA + "dev/uwu/dimension/client/KeeperSpeechBubbles.java",
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(["git", *args])


def exact_hook(path, edits):
    expected = git("show", BASELINE + ":" + path)
    for before, after in edits:
        require(expected.count(before) == 1, "Native hook anchor changed: " + path)
        expected = expected.replace(before, after, 1)
    require(Path(path).read_bytes() == expected, "Unrelated native behavior changed: " + path)


def verify(candidate, baseline, classes):
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Run dialogue validation only on GitHub Actions")
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
        require(path in PRODUCTION or path.startswith(("dialogue-check/", "dialogue-ci/"))
                or path == ".github/workflows/villager-dialogue.yml",
                "Changed file outside dialogue scope: " + path)
    require(PRODUCTION <= changed, "Expected dialogue production files are missing")
    for path in ("build.gradle", MOD_INFO):
        original = git("show", BASELINE + ":" + path)
        require(original.count(BASELINE_VERSION.encode()) == 1, "Ambiguous baseline version")
        require(Path(path).read_bytes() == original.replace(BASELINE_VERSION.encode(), VERSION.encode(), 1),
                "Dependency/build metadata changed beyond the candidate version: " + path)
    exact_hook(JAVA + "dev/uwu/dimension/Keeper.java", [(
        b'        player.displayClientMessage(Component.literal(KeeperCraft.of(this).greeting), true);\r\n',
        b'        KeeperSpeech.say(this, getDisplayName().copy().append(": ").append(KeeperCraft.of(this).greeting));\r\n')])
    exact_hook(JAVA + "dev/uwu/dimension/UwUDimension.java", [(
        b'        People.register(bus);\r\n',
        b'        People.register(bus);\r\n        KeeperSpeech.register(bus);\r\n')])
    exact_hook(JAVA + "dev/uwu/dimension/SakuraAmbientDialogue.java", [
        (b'/** Private, opt-in chat conversations with eligible Sakura Reach residents. */',
         b'/** Opt-in conversations: nearby spoken lines, with private choices and session controls. */'),
        (b'        player.sendSystemMessage(scene.opening().component());',
         b'        KeeperSpeech.say((Keeper) resident, scene.opening().component());'),
        (b'            player.sendSystemMessage(choice.reply().component());',
         b'            KeeperSpeech.say((Keeper) resident, choice.reply().component());'),
    ])

    candidate = candidate.resolve()
    digest = sha256(candidate.read_bytes()).hexdigest()
    require(candidate == Path(os.environ["UWU_JAR"]).resolve(), "A suite selected a different candidate")
    require(digest == os.environ["UWU_EXPECTED_JAR_SHA256"], "Frozen candidate hash changed")
    require(sha256(baseline.read_bytes()).hexdigest() == BASELINE_DIGEST, "Wrong integrated baseline artifact")
    allowed_classes = {p[len(JAVA):].removesuffix(".java") for p in PRODUCTION if p.startswith(JAVA)}
    preserved = []
    altered = []
    with ZipFile(candidate) as new, ZipFile(baseline) as old:
        new_names = [i.filename for i in new.infolist() if not i.is_dir()]
        old_names = [i.filename for i in old.infolist() if not i.is_dir()]
        require(len(new_names) == len(set(new_names)) and len(old_names) == len(set(old_names)),
                "Duplicate packaged entries")
        require(new.testzip() is None and old.testzip() is None, "Corrupt JAR")
        for name in sorted(set(new_names) | set(old_names)):
            same = name in new_names and name in old_names and new.read(name) == old.read(name)
            allowed = name == "META-INF/neoforge.mods.toml" or (name.endswith(".class")
                    and name.removesuffix(".class").split("$", 1)[0] in allowed_classes)
            if not same:
                require(allowed, "Packaged content outside dialogue scope changed: " + name)
                altered.append(name)
            elif not allowed:
                preserved.append(name)
        require(not any(name.startswith(("dev/uwu/dialoguecheck/", "dev/jvitor/", "dialogue-check/"))
                        or "chatbubbles" in name.lower() for name in new_names),
                "Reference mod or fixture content leaked into the product")
        require(b"chatbubbles" not in new.read("META-INF/neoforge.mods.toml").lower(),
                "Chat Bubbles must not be a product dependency")
        require(f'version="{VERSION}"'.encode() in new.read("META-INF/neoforge.mods.toml"), "Wrong version")
        for row in git("ls-files", "-z", "src/main/resources").split(b"\0"):
            if row:
                source = Path(row.decode())
                name = source.as_posix()[len(RESOURCES):]
                require(name in new_names and new.read(name) == source.read_bytes(),
                        "Packaged resource differs from committed source: " + name)
        packaged = {name for name in new_names if name.endswith(".class")}
        compiled = {p.relative_to(classes).as_posix() for p in classes.rglob("*.class")}
        require(packaged == compiled, "Packaged main-class inventory differs from the current compilation")
        for name in packaged:
            require(new.read(name) == (classes / name).read_bytes(), "Stale packaged class: " + name)
    report = {
        "source_sha": head,
        "baseline_source_sha": BASELINE,
        "baseline_jar_sha256": BASELINE_DIGEST,
        "candidate_jar_sha256": digest,
        "version": VERSION,
        "changed_source_files": sorted(changed),
        "approved_production_files": sorted(PRODUCTION),
        "changed_jar_entries": altered,
        "unchanged_entries_outside_dialogue": len(preserved),
        "no_reference_dependency_or_bundling": True,
        "worldgen_dialogue_text_assets_weapons_books_unchanged": True,
    }
    folder = Path("build/dialogue-ci")
    folder.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(report, indent=2) + "\n").encode()
    record = folder / "scope.json"
    if record.exists():
        require(record.read_bytes() == encoded, "Source/scope/candidate identity changed during validation")
    else:
        record.write_bytes(encoded)
    print(f"KEEPER SPEECH SCOPE PASS unchanged={len(preserved)} source={head} jar={digest}")


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--classes", type=Path, default=Path("build/classes/java/main"))
    args = parser.parse_args()
    verify(args.candidate, args.baseline, args.classes)
