"""Compare the cumulative product with the installed base and the four approved hosted JARs."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import io
import json
import os
import subprocess

BASE = "1f489c5308b686a30672ec19b671a64534eb28a4"
PRODUCT = "8c243207057d764cb21270d4308eaa0f9364b6ec"
SOURCES = {
    "guardian": "2b55370ce497d30c56e5ce1079cb13d3f8de5d69",
    "dusk": "bb83a9f1a34edb761c5a51a5698e2d80cde914fe",
    "blossom": "a520e942d2d403b7846dd946b0ecee882c41545d",
    "dialogue": "cad6db2e5db80fb656940266d1fd1298b2290b95",
    "weapons": "2754f089fdd4e01d43f77cf437164556b9ce8654",
    "tail": "c46de3f9ac7a613dc500270f84cef2e841c56ccc",
}
REFERENCES = {
    "base": (14723910544, "0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f"),
    "dusk": (14732183290, "b37fd8857243c407a5a4209bafaa5bbf918666cf52a6d6565daad79fd8af1356"),
    "blossom": (14732748892, "37a980018c5b5282cd4b1318a3bbeba9f7fb324609b1f0d34afacbea776348d4"),
    "dialogue": (14728344147, "d352d38abb24e7edb90ccb2230dbf96dd904bfb3c3ab6c90a2e962dfbd95c2cf"),
    "weapons": (14730223076, "632de2bc1dd984b94857538edd7f5994a962d59fdba3edf35ee737faca9cb5ee"),
}
COMPANION = (14732607426, "dfb30102e24ce78f88927f9579acfee63da84167989221da95adebae157d6b83")
META = "META-INF/neoforge.mods.toml"
TAIL = "assets/uwudimension/models/block/seated_cupid_twin_tails.obj"
VERSIONS = {"build.gradle", "src/main/resources/" + META}
INPUT = Path("build/cumulative/input")


def check(ok, message):
    if not ok:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", *args])


def download(artifact, expected):
    data = subprocess.check_output(["gh", "api", f"repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/{artifact}/zip"])
    check(sha256(data).hexdigest() == expected, f"GitHub artifact {artifact} digest mismatch")
    return data


def members(data):
    with ZipFile(io.BytesIO(data)) as z:
        files = [n for n in z.namelist() if not n.endswith("/")]
        check(len(files) == len(set(files)), "Duplicate packaged entry")
        return {n: z.read(n) for n in files}


def verify_sources():
    head = os.environ["GITHUB_SHA"]
    check(head == git("rev-parse", "HEAD").decode().strip(), "Unexpected checkout")
    check(not git("diff", PRODUCT, head, "--", "src", "build.gradle"), "Frozen production tree changed")
    check(not git("diff", "HEAD", "--"), "Checkout was modified during the build")
    expected = {}
    for name, rev in SOURCES.items():
        subprocess.run(["git", "merge-base", "--is-ancestor", rev, head], check=True)
        for path in git("diff", "--name-only", BASE, rev, "--", "src").decode().splitlines():
            if path in VERSIONS:
                continue
            check(path not in expected, f"Multiple approved sources claim {path}")
            expected[path] = name
            check(git("show", f"HEAD:{path}") == git("show", f"{rev}:{path}"), f"Wrong {name} source for {path}")
    changed = set(git("diff", "--name-only", BASE, head, "--", "src", "build.gradle").decode().splitlines())
    check(changed == set(expected) | VERSIONS, "Aggregate production path allowlist mismatch")
    old = b"1.5.0-beta.13-combined-rc.2-playtest.9-integrated.1"
    for path in VERSIONS:
        before = git("show", f"{BASE}:{path}")
        check(before.count(old) == 1 and Path(path).read_bytes() == before.replace(old, old[:-1]+b"2"), path)
    original = git("rev-parse", f"{BASE}:src/main/resources/{TAIL}").decode().strip()
    edited = git("rev-parse", f"HEAD:src/main/resources/{TAIL}").decode().strip()
    check((original, edited) == ("dfa4addc4687e60aae6f300beee76a5ccd8224ef",
                                  "447800ffad8ebcf8b4d64a66b79df11395e0b118"), "Statue blob mismatch")
    return expected


def verify_jar(candidate):
    baseline_bytes = download(*REFERENCES["base"])
    Path(os.environ["UWU_BASELINE_JAR"]).write_bytes(baseline_bytes)
    baseline = members(baseline_bytes)
    combined = baseline.copy()
    owners = {}
    for name in ("dusk", "blossom", "dialogue", "weapons"):
        approved = members(download(*REFERENCES[name]))
        for entry in baseline.keys() | approved.keys():
            if entry == META or baseline.get(entry) == approved.get(entry):
                continue
            check(entry not in owners, f"Overlapping packaged entry {entry}")
            owners[entry] = name
            if entry in approved:
                combined[entry] = approved[entry]
            else:
                del combined[entry]
    # The Guardian run returned no intermediate JAR. Bind only its two approved Java
    # sources (including nested classes) to the actual output of this build.
    classes = Path("build/classes/java/main")
    for simple in ("BlossomWarden", "GuardianWarnings"):
        compiled = {f.relative_to(classes).as_posix(): f.read_bytes()
                    for f in (classes/"dev/uwu/dimension").glob(simple + "*.class")
                    if f.stem.split("$")[0] == simple}
        prefix = "dev/uwu/dimension/" + simple
        check(prefix + ".class" in compiled, "Missing Guardian class " + simple)
        for entry in list(combined):
            if entry.endswith(".class") and entry[:-6].split("$")[0] == prefix:
                del combined[entry]
        for entry, data in compiled.items():
            check(entry not in owners, "Guardian class overlaps another feature")
            combined[entry] = data
            if data != baseline.get(entry):
                owners[entry] = "guardian"
    for entry, owner in [(TAIL, "tail"), (META, "combined version")]:
        check(entry not in owners, "Unexpected resource overlap")
        combined[entry] = (Path("src/main/resources")/entry).read_bytes()
        owners[entry] = owner
    actual = members(candidate.read_bytes())
    wrong = sorted(p for p in combined.keys() | actual.keys() if combined.get(p) != actual.get(p))
    check(not wrong, f"Unapproved packaged differences ({len(wrong)}): {wrong[:30]}")
    return owners, len(actual)


def prepare_companion():
    # This separately distributed companion is an input to the Dusk settings check only.
    with ZipFile(io.BytesIO(download(*COMPANION))) as bundle:
        jars = [n for n in bundle.namelist() if n.split("/")[-1] == "UnyieldingQuests-1.6.9.jar"]
        check(len(jars) == 1, "Expected the single approved Dusk settings companion")
        Path(os.environ["UWU_COMPANION_JAR"]).write_bytes(bundle.read(jars[0]))


if __name__ == "__main__":
    check(os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted" and
          os.environ.get("GITHUB_REPOSITORY") == "WeepingAngeIs/z-UwU_dm" and
          json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())["repository"]["private"] is False,
          "Expected the public GitHub-hosted run")
    INPUT.mkdir(exist_ok=True, parents=True)
    candidate = Path(os.environ["UWU_JAR"])
    paths = verify_sources()
    entries, count = verify_jar(candidate)
    prepare_companion()
    report = dict(source=os.environ["GITHUB_SHA"], product=PRODUCT, baseline=BASE, sources=SOURCES,
                  approved_source_paths=paths, approved_packaged_entries=entries, total_entries=count,
                  candidate_sha256=sha256(candidate.read_bytes()).hexdigest(), reference_artifacts=REFERENCES)
    (INPUT/"scope.json").write_text(json.dumps(report, sort_keys=True, indent=2)+"\n")
    print(f"CUMULATIVE BYTE COMPARISON PASS entries={count} approved_entries={len(entries)} sha256={report['candidate_sha256']}")
