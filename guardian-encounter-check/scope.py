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
VILLAGE_RUN = 44988599623
VILLAGE_INPUT = 14740668957
ARCHIVE_DIGEST = "sha256:f22703c32fbf3c6dc2963c65671873c1a653b72a7b070264c07c556abf3c3ed1"
VILLAGE_HASH = "5cd27d5e473f58835dbfda1ef639cf98c61fda3d662c122e924bb7be1a89fb1d"

def gh(path):
    return subprocess.check_output(["gh", "api", f"repos/{REPO}/{path}"])

run = json.loads(gh(f"actions/runs/{VILLAGE_RUN}"))
assert run["head_sha"] == BASELINE_COMMIT
verified_jobs = {j["name"] for j in json.loads(gh(f"actions/runs/{VILLAGE_RUN}/jobs"))["jobs"] if j["conclusion"] == "success"}
assert {"package", "generate (152045)", "generate (20261121)", "generate (8675309)"} <= verified_jobs
raw = gh(f"actions/artifacts/{VILLAGE_INPUT}/zip")
assert "sha256:" + sha256(raw).hexdigest() == ARCHIVE_DIGEST
with ZipFile(BytesIO(raw)) as bundle:
    baseline = bundle.read("candidate.jar")
    assert json.loads(bundle.read("scope.json"))["candidateSha256"] == VILLAGE_HASH
assert sha256(baseline).hexdigest() == VILLAGE_HASH

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
report = dict(villageSource=BASELINE_COMMIT, villageArchive=VILLAGE_INPUT, villageSHA256=VILLAGE_HASH,
              candidateSHA256=sha256(candidate.read_bytes()).hexdigest(), identicalEntries=len(entries)-len(changes), changedEntries=changes)
out.write_text(json.dumps(report, indent=2)+"\n")
print(json.dumps(report, indent=2))
