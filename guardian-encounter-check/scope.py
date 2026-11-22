"""Compare the exact packaged Guardian candidate with the verified village candidate."""
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
import sys
from zipfile import ZipFile

REPO = "WeepingAngeIs/z-UwU_dm"
BASELINE_COMMIT = "c509523831c5f94af42a3b568c3a24e710dbeda6"
baseline_name = "uwu-village-playtest-" + BASELINE_COMMIT

def gh(path):
    return subprocess.check_output(["gh", "api", f"repos/{REPO}/{path}"])

matching = json.loads(gh("actions/artifacts?name=" + baseline_name))["artifacts"]
archive, = [item for item in matching if not item["expired"] and item["workflow_run"]["head_sha"] == BASELINE_COMMIT]
run = json.loads(gh("actions/runs/" + str(archive["workflow_run"]["id"])))
assert run["conclusion"] == "success" and run["head_sha"] == BASELINE_COMMIT, "Village base was not verified"
raw = gh(f'actions/artifacts/{archive["id"]}/zip')
assert "sha256:" + sha256(raw).hexdigest() == archive["digest"]
with ZipFile(BytesIO(raw)) as bundle:
    fname, = [n for n in bundle.namelist() if n.endswith("-villages.1.jar")]
    baseline = bundle.read(fname)
    scope_name, = [n for n in bundle.namelist() if n.endswith("village-checks.json")]
    predecessor_hash = json.loads(bundle.read(scope_name))["scope"]["candidateSha256"]
assert sha256(baseline).hexdigest() == predecessor_hash

candidate = Path(sys.argv[1])
out = Path(sys.argv[2])
class_pat = re.compile(r"dev/uwu/dimension/(BlossomVillage|BlossomWarden|BlossomWardenRenderer|GuardianCommands|GuardianFamily|GuardianVillageHomes|GuardianWarnings|GuardianDepartureGoal|GuardianWarningMotion|MaleGuardianModel|People)(\$[^/]*)?\.class")
resource_prefix = "assets/uwudimension/"
resources = {
    "lang/en_us.json", "geometry/petal_guardian_male.mesh", "models/item/uwu_guardian_spawn_egg.json",
    "textures/entity/petal_guardian_male.png", "textures/entity/petal_guardian_male_wink.png",
    "textures/entity/petal_guardian_female_wink.png", "textures/entity/dusk_guardian_male.png",
    "textures/entity/dusk_guardian_lily_pad.png",
}
for suffix in ["", "_half", "_blink", "_half_lips", "_blink_lips"]:
    resources.add("textures/entity/dusk_guardian_female" + suffix + ".png")
for sex in ["male", "girl"]:
    resources.update({f"textures/item/dusk_guardian_{sex}_egg.png", f"models/item/dusk_guardian_{sex}_egg.json"})
allowed_resources = {resource_prefix + p for p in resources} | {"META-INF/MANIFEST.MF", "META-INF/neoforge.mods.toml"}
with ZipFile(BytesIO(baseline)) as old, ZipFile(candidate) as new:
    changes = []
    entries = sorted({i.filename for z in (old,new) for i in z.infolist() if not i.is_dir()})
    for name in entries:
        if name in old.NameToInfo and name in new.NameToInfo and old.read(name) == new.read(name):
            continue
        assert name in allowed_resources or class_pat.fullmatch(name), f"Unexpected cumulative package change: {name}"
        assert name in new.NameToInfo, f"Lost an existing package entry: {name}"
        changes.append(name)
    assert all(resource_prefix + p in new.NameToInfo for p in resources)
    assert all("dev/uwu/dimension/" + name + ".class" in changes for name in ("BlossomWarden", "GuardianWarnings", "MaleGuardianModel"))
out.parent.mkdir(exist_ok=True,parents=True)
report = dict(villageSource=BASELINE_COMMIT, villageArchive=archive["id"], villageSHA256=predecessor_hash,
              candidateSHA256=sha256(candidate.read_bytes()).hexdigest(), identicalEntries=len(entries)-len(changes), changedEntries=changes)
out.write_text(json.dumps(report, indent=2)+"\n")
print(json.dumps(report, indent=2))
